#!/usr/bin/env python3
"""Sondagem ADB temporária, sem instalar APK ou conceder permissões ao Home."""
import argparse
import base64
import json
import os
from pathlib import Path
import queue
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parent
REMOTE = '/data/local/tmp/fmtv-media-session-probe.zip'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', help='Executa pause/pause/play/play e restaura o estado inicial desta sessão')
    parser.add_argument('--seek', action='store_true', help='Com --package, mede saltos +10/-10 segundos em vez de play/pause')
    parser.add_argument('--transport-seek', action='store_true', help='Mede fastForward/rewind da sessao, sem tecla global')
    parser.add_argument('--media-button', action='store_true', help='Com --transport-seek, envia a tecla apenas para a sessao alvo')
    parser.add_argument('--start-playing', action='store_true', help='Retoma antes dos comandos e restaura estado/posicao originais depois')
    parser.add_argument('--output', type=Path, required=True, help='Diretório local de evidências')
    args = parser.parse_args()
    if (args.seek or args.transport_seek) and not args.package: parser.error('Seek requer --package')
    adb = ['adb', '-s', args.serial]
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', Path.home() / 'Library/Android/sdk'))
    java = Path('/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home')
    android = sdk / 'platforms/android-35/android.jar'
    tools = sdk / 'build-tools/35.0.1'
    args.output.mkdir(parents=True, exist_ok=True)
    observations = {'serial': args.serial, 'startedAt': time.time(), 'source': 'adb-shell-MediaController; nao e o processo Home', 'events': [], 'screenshots': {}}
    with tempfile.TemporaryDirectory(prefix='fmtv-media-session-') as temp:
        build = Path(temp)
        subprocess.run([str(java/'bin/javac'), '--release', '8', '-cp', str(android), '-d', str(build), str(ROOT/'FmtvMediaSessionProbe.java')], check=True)
        subprocess.run([str(tools/'d8'), '--lib', str(android), '--min-api', '28', '--output', str(build/'probe.zip'), str(build/'FmtvMediaSessionProbe.class')], check=True, env={**os.environ, 'JAVA_HOME': str(java)})
        subprocess.run(adb + ['push', str(build/'probe.zip'), REMOTE], check=True, stdout=subprocess.DEVNULL)
        process = subprocess.Popen(adb + ['shell', '-T', f'CLASSPATH={REMOTE} app_process /system/bin FmtvMediaSessionProbe'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
        lines = queue.Queue()

        def read_lines():
            for line in process.stdout:
                lines.put(line)
            lines.put(None)

        threading.Thread(target=read_lines, daemon=True).start()

        def request(action, **fields):
            process.stdin.write(json.dumps({'action': action, **fields}) + '\n')
            process.stdin.flush()
            deadline = time.monotonic() + 12
            while time.monotonic() < deadline:
                try: line = lines.get(timeout=max(0, deadline-time.monotonic()))
                except queue.Empty: break
                if not line: break
                if not line.startswith('{'): continue
                response = json.loads(line)
                if 'error' in response: raise RuntimeError(response['error'])
                return response
            raise RuntimeError('A sondagem nao respondeu em 12 s')

        def observe(action, **fields):
            response = request(action, **fields)
            observations['events'].append({'action': action, **response})
            return response['result']

        def screenshot(name):
            data = subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=15)
            if not data.startswith(b'\x89PNG\r\n\x1a\n'):
                observations['screenshots'][name] = 'Captura indisponivel; requer observacao humana'
                return
            (args.output/f'{name}.png').write_bytes(data)
            observations['screenshots'][name] = f'{name}.png'

        initial = None
        target = None
        initial_at = None
        try:
            snapshot = observe('snapshot')
            print(json.dumps(snapshot, ensure_ascii=False, indent=2), flush=True)
            if args.package:
                candidates = [s for s in snapshot['sessions'] if s['package'] == args.package]
                if len(candidates) != 1: raise RuntimeError('Esperava exatamente uma sessao ativa do pacote')
                initial = candidates[0]
                initial_at = time.monotonic()
                if initial['state'] not in [2, 3]: raise RuntimeError('Sessao inicial precisa estar tocando ou pausada')
                target = {'target': initial['id'], 'item': initial['item']}
                picture = request('art', **target)['result']
                if picture.get('available'):
                    (args.output/'artwork.jpg').write_bytes(base64.b64decode(picture['jpeg']))
                    observations['artwork'] = 'artwork.jpg'
                screenshot('before')
                if args.start_playing:
                    result = observe('play', **target)
                    if not result['matched']: raise RuntimeError('Reproducao inicial nao confirmada')
                    time.sleep(1)
                steps = [('seek', {'offsetMillis': 10000}), ('seek', {'offsetMillis': -10000})] if args.seek else [(a, {}) for a in (['forward', 'backward'] if args.transport_seek else ['pause', 'pause', 'play', 'play'])]
                for index, (action, fields) in enumerate(steps):
                    result = observe(action, **target, **fields, **({'mediaButton': True} if args.media_button else {}))
                    print(json.dumps({'action': action, **fields, 'attempt': index+1, 'matched': result['matched'], 'elapsedMillis': result['elapsedMillis'], 'state': result['after']['state'], 'positionMillis': result['after']['positionMillis']}, ensure_ascii=False), flush=True)
                    if not result['matched']: raise RuntimeError(f'{action}: estado nao confirmado')
                    time.sleep(1)
                    observe('snapshot')
                    if args.seek or args.transport_seek: screenshot('seek-forward' if index == 0 else 'seek-backward')
                    elif index in [0, 2]: screenshot('paused' if action == 'pause' else 'playing')
                # Alvo retirado deve ser rejeitado, sem despachar comando a outra sessão.
                try:
                    request('pause', target='invalid-retired-session', item=initial['item'])
                    raise RuntimeError('Alvo inexistente foi aceito')
                except RuntimeError as error:
                    if str(error) != 'session-ended': raise
                    observations['staleTargetRejected'] = True
                try:
                    request('pause', target=initial['id'], item='invalid-old-item')
                    raise RuntimeError('Obra antiga foi aceita')
                except RuntimeError as error:
                    if str(error) != 'item-changed': raise
                    observations['staleItemRejected'] = True
        finally:
            if initial is not None and target is not None:
                try:
                    if (args.seek or args.transport_seek) and initial_at is not None:
                        position = initial['positionMillis'] + (int((time.monotonic()-initial_at)*1000) if initial['state'] == 3 else 0)
                        observe('seek', positionMillis=position, **target)
                    observe('play' if initial['state'] == 3 else 'pause', **target)
                except Exception as error: observations['restoreError'] = str(error)
            observations['finishedAt'] = time.time()
            (args.output/'observations.json').write_text(json.dumps(observations, ensure_ascii=False, indent=2)+'\n')
            process.stdin.close()
            try: process.wait(timeout=3)
            except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=3)
            stderr = process.stderr.read()
            if stderr: print(stderr.strip())
            subprocess.run(adb + ['shell', 'rm', '-f', REMOTE], check=True)
    print(f'Evidencias: {args.output.resolve()}')


if __name__ == '__main__':
    main()

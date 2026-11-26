"""Launches the Lexplosion Forge 1.8.9 setup like the launcher does, in a separate game folder, offline."""
import json, os, shutil, subprocess, sys

LX = os.path.join(os.environ['APPDATA'], '.lexplosion')
GAME = sys.argv[1]
JAR = sys.argv[2]
extra = sys.argv[3:]

libs = json.load(open(os.path.join(LX, 'versions/libraries/1.8.9-Forge-11.15.1.2318.json'), encoding='utf-8'))
cp = []
for path, info in libs.items():
    if info.get('notLaunch'):
        continue
    full = os.path.join(LX, 'libraries', path)
    if os.path.isfile(full):
        cp.append(full)
    else:
        print('missing', path)

os.makedirs(os.path.join(GAME, 'mods'), exist_ok=True)
for f in os.listdir(os.path.join(GAME, 'mods')):
    if f.startswith('zerovisual-'):
        os.remove(os.path.join(GAME, 'mods', f))
for j in [JAR] + [a for a in extra if a.endswith('.jar')]:
    shutil.copy(j, os.path.join(GAME, 'mods'))
opts = os.path.join(LX, 'instances', 'zerovisual 1.8.9', 'options.txt')
if not os.path.exists(os.path.join(GAME, 'options.txt')):
    shutil.copy(opts, GAME)

java = os.path.join(LX, 'java/versions/jre-legacy/bin/java.exe')
natives = os.path.join(LX, 'natives', '1.8.9')
cmd = [java, '-Xmx2G', '-Djava.library.path=' + natives, '-cp', os.pathsep.join(cp),
       'net.minecraft.launchwrapper.Launch',
       '--username', 'ZvTest', '--version', '1.8.9', '--gameDir', GAME,
       '--assetsDir', os.path.join(LX, 'assets'), '--assetIndex', '1.8',
       '--uuid', '00000000000000000000000000000000', '--accessToken', '0', '--userProperties', '{}', '--userType', 'legacy',
       '--width', '1280', '--height', '720',
       '--tweakClass', 'net.minecraftforge.fml.common.launcher.FMLTweaker', '--versionType', 'Forge']
env = dict(os.environ)
env.update({k.split('=', 1)[0]: k.split('=', 1)[1] for k in extra if '=' in k and not k.endswith('.jar')})
p = subprocess.Popen(cmd, cwd=GAME, env=env, stdout=open(os.path.join(GAME, 'stdout.log'), 'w'), stderr=subprocess.STDOUT)
print('PID', p.pid)

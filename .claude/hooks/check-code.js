// Hook PostToolUse (Edit|Write): valida sintaxis del archivo recién editado según su extensión.
// Si el chequeo falla, exit 2 → el error le llega a Claude al instante, antes de que el
// código roto viaje a ningún lado.
//
// ADAPTAR AL STACK: el que monta el proyecto deja activas SOLO las extensiones que apliquen
// (descomentar/agregar líneas en CHECKS). Si la herramienta no está instalada, el hook
// se salta el chequeo en silencio (no rompe nada por defecto).

const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');

// Mapa extensión → [comando, args]. '{file}' se reemplaza por el path editado.
const CHECKS = {
  '.sh': ['bash', ['-n', '{file}']],
  // '.py': ['python', ['-m', 'py_compile', '{file}']], // en Windows a veces es 'py'
  // '.ts': ['npx', ['tsc', '--noEmit']],   // requiere tsconfig.json (chequea el proyecto entero)
  // '.tsx': ['npx', ['tsc', '--noEmit']],
};

let raw = '';
process.stdin.on('data', (c) => (raw += c));
process.stdin.on('end', () => {
  let file = '';
  try {
    file = String(JSON.parse(raw).tool_input?.file_path || '');
  } catch {
    process.exit(0);
  }
  const ext = path.extname(file).toLowerCase();
  const check = CHECKS[ext];
  if (!check) process.exit(0);

  // tsc solo tiene sentido si el proyecto tiene tsconfig.json
  if ((ext === '.ts' || ext === '.tsx')
      && !fs.existsSync(path.join(process.env.CLAUDE_PROJECT_DIR || '.', 'tsconfig.json'))) {
    process.exit(0);
  }

  const [cmd, args] = check;
  // npx en Windows es un .cmd → necesita shell; con shell, los args con espacios van citados.
  const useShell = process.platform === 'win32' && cmd === 'npx';
  const finalArgs = args
    .map((a) => a.replace('{file}', file))
    .map((a) => (useShell && /\s/.test(a) ? '"' + a + '"' : a));
  try {
    execFileSync(cmd, finalArgs, {
      stdio: ['ignore', 'ignore', 'pipe'],
      cwd: process.env.CLAUDE_PROJECT_DIR || undefined,
      shell: useShell,
    });
    process.exit(0);
  } catch (e) {
    if (e.code === 'ENOENT') process.exit(0); // herramienta no instalada → no bloquear
    const err = e.stderr ? e.stderr.toString() : (e.stdout ? e.stdout.toString() : String(e));
    process.stderr.write(cmd + ' detectó un error de sintaxis en ' + file + ':\n' + err);
    process.exit(2);
  }
});

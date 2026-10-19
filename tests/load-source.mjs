import fs from "node:fs";
import { createRequire } from "node:module";
import ts from "typescript";
const nativeRequire = createRequire(import.meta.url);

export function loadSource(path, { imports = {}, env = {}, vueSetup = false } = {}) {
  let source = fs.readFileSync(path, "utf8");
  if (vueSetup) {
    const { parse, compileScript } = nativeRequire("@vue/compiler-sfc");
    source = compileScript(parse(source).descriptor, { id: "regression" }).content;
  }
  source = source.replaceAll("import.meta.env", JSON.stringify(env));
  const code = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  new Function("require", "module", "exports", code)(
    (name) => name in imports ? imports[name] : name.endsWith(".vue") ? {} : nativeRequire(name), module, module.exports,
  );
  return module.exports;
}

export function memoryStorage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
}

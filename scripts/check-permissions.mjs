#!/usr/bin/env node
/**
 * TECH-09：权限码三方一致性检查。
 * 权威：apps/api PermissionCode 枚举；镜像：packages/shared；文档：权限矩阵。
 * 任一方新增/删除而其余未同步，CI 失败。
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const paths = {
  java: resolve(root, "apps/api/src/main/java/cn/edu/scuec/icpc/lab/security/PermissionCode.java"),
  ts: resolve(root, "packages/shared/src/permissions.ts"),
  matrix: resolve(root, "docs/security/权限矩阵.md"),
};

const read = (path) => readFileSync(path, "utf8");

function extract(text, pattern) {
  const set = new Set();
  for (const match of text.matchAll(pattern)) {
    set.add(match[1]);
  }
  return set;
}

const CODE_PATTERN = /["']([a-z][a-z0-9]*(?::[a-z][a-z0-9]*)+)["']/g;

const javaCodes = extract(read(paths.java), CODE_PATTERN);
const tsCodes = extract(read(paths.ts), CODE_PATTERN);
const matrix = read(paths.matrix);

const problems = [];
const missingInTs = [...javaCodes].filter((code) => !tsCodes.has(code)).sort();
const extraInTs = [...tsCodes].filter((code) => !javaCodes.has(code)).sort();
const missingInMatrix = [...javaCodes].filter((code) => !matrix.includes(code)).sort();
const duplicatesInTs = [...read(paths.ts).matchAll(new RegExp(CODE_PATTERN.source, "g"))]
  .map((m) => m[1])
  .filter((code, index, all) => all.indexOf(code) !== index);

if (missingInTs.length) problems.push(`shared 缺少: ${missingInTs.join(", ")}`);
if (extraInTs.length) problems.push(`shared 多出: ${extraInTs.join(", ")}`);
if (missingInMatrix.length) problems.push(`权限矩阵缺少: ${missingInMatrix.join(", ")}`);
if (duplicatesInTs.length) problems.push(`shared 重复: ${[...new Set(duplicatesInTs)].join(", ")}`);

if (problems.length > 0) {
  console.error("权限码检查失败：");
  for (const problem of problems) console.error(` - ${problem}`);
  process.exit(1);
}

console.log(`权限码一致：${javaCodes.size} 个（后端枚举 / shared / 权限矩阵）`);
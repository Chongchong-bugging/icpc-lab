import { useEffect, useState } from "react";
import { ROLE_CODES } from "@icpc/shared";

type Health = {
  status: string;
  service: string;
  version: string;
  time: string;
};

type State =
  | { kind: "loading" }
  | { kind: "ok"; data: Health }
  | { kind: "error"; message: string };

export default function App() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v1/health", { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        return (await response.json()) as Health;
      })
      .then((data) => setState({ kind: "ok", data }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setState({ kind: "error", message: error instanceof Error ? error.message : "未知错误" });
        }
      });
    return () => controller.abort();
  }, []);

  return (
    <main className="page">
      <header className="header">
        <p className="eyebrow">SCUEC ICPC LAB</p>
        <h1>实验室管理系统</h1>
        <p className="subtitle">G0 基线：前端可构建、后端可启动、健康检查连通。</p>
      </header>

      <section className="card" aria-live="polite">
        <h2>服务状态</h2>
        {state.kind === "loading" && <p className="muted">正在检查后端…</p>}
        {state.kind === "error" && (
          <p className="error">
            后端未连通（{state.message}）。请先运行 <code>mvn spring-boot:run</code>。
          </p>
        )}
        {state.kind === "ok" && (
          <dl className="status">
            <div>
              <dt>状态</dt>
              <dd className="ok">{state.data.status}</dd>
            </div>
            <div>
              <dt>服务</dt>
              <dd>{state.data.service}</dd>
            </div>
            <div>
              <dt>版本</dt>
              <dd>{state.data.version}</dd>
            </div>
            <div>
              <dt>时间</dt>
              <dd>{new Date(state.data.time).toLocaleString("zh-CN")}</dd>
            </div>
          </dl>
        )}
      </section>

      <section className="card">
        <h2>角色基线</h2>
        <ul className="roles">
          {ROLE_CODES.map((role) => (
            <li key={role}>{role}</li>
          ))}
        </ul>
        <p className="muted">权限码以后端枚举为权威，前端只做展示；详见 docs/security/权限矩阵.md。</p>
      </section>
    </main>
  );
}
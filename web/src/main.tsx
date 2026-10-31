import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { registrar } from "./diagnostico";
import { iniciarPreferencias } from "./preferencias";
import { PROTOCOL_VERSION } from "./tipos";
import { App } from "./ui/App";
import "./estilos.css";

// Tema e idioma antes del primer render: sin parpadeo blanco ni textos que cambian.
iniciarPreferencias();
registrar("!", "arranque", `${location.pathname} · protocolo ${PROTOCOL_VERSION}`);

const root = document.getElementById("root");
if (!root) throw new Error("Falta #root en index.html");

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

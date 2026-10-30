import { useEffect, useMemo, useSyncExternalStore } from "react";
import { Cliente, urlWebSocket } from "../net/cliente";
import type { EstadoConexion } from "../net/cliente";
import { Ingreso } from "./Ingreso";
import { Lobby } from "./Lobby";
import { Mesa } from "./Mesa";

/**
 * Singleton: con React StrictMode el componente se monta dos veces en desarrollo
 * y no queremos abrir dos WebSockets.
 */
let unico: Cliente | null = null;

function obtenerCliente(): Cliente {
  if (!unico) unico = new Cliente(urlWebSocket());
  return unico;
}

export function App() {
  const cliente = useMemo(obtenerCliente, []);
  const estado: EstadoConexion = useSyncExternalStore(cliente.suscribir, cliente.obtener);

  useEffect(() => {
    cliente.iniciar();
  }, [cliente]);

  useEffect(() => {
    const alVolver = () => {
      if (document.visibilityState === "visible") cliente.reintentarAhora();
    };
    document.addEventListener("visibilitychange", alVolver);
    window.addEventListener("online", alVolver);
    return () => {
      document.removeEventListener("visibilitychange", alVolver);
      window.removeEventListener("online", alVolver);
    };
  }, [cliente]);

  useEffect(() => {
    if (!estado.aviso) return;
    const timer = setTimeout(() => cliente.limpiarAviso(), 4000);
    return () => clearTimeout(timer);
  }, [estado.aviso, cliente]);

  if (estado.motivo) return <Cerrado motivo={estado.motivo} />;

  if (estado.miId === null) {
    return (
      <Ingreso
        conectado={estado.fase !== "iniciando" && estado.fase !== "cerrado"}
        nombreInicial={cliente.nombreGuardado()}
        aviso={estado.aviso}
        onEntrar={(nombre) => cliente.entrar(nombre)}
      />
    );
  }

  if (estado.vista) {
    return <Mesa vista={estado.vista} cliente={cliente} eventos={estado.eventos} reconectando={estado.fase !== "online"} />;
  }

  if (estado.lobby) {
    return <Lobby lobby={estado.lobby} miId={estado.miId} onListo={(listo) => cliente.listo(listo)} />;
  }

  return (
    <div className="pantalla centrada">
      <p className="cargando">Entrando a la sala…</p>
    </div>
  );
}

function Cerrado({ motivo }: { motivo: string }) {
  return (
    <div className="pantalla centrada">
      <div className="tarjeta">
        <h1>Se cerró la mesa</h1>
        <p>{motivo === "HOST_LEFT" ? "El anfitrión cerró la partida." : motivo}</p>
        <button className="primario" onClick={() => window.location.reload()}>
          Volver a entrar
        </button>
      </div>
    </div>
  );
}

import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { registrar } from "../diagnostico";
import { t, usarIdioma } from "../i18n";
import { Cliente, urlWebSocket } from "../net/cliente";
import type { EstadoConexion } from "../net/cliente";
import { cambiarPreferencias } from "../preferencias";
import { desbloquearAudio } from "../sonido";
import { Ingreso } from "./Ingreso";
import { Lobby } from "./Lobby";
import { Mesa } from "./Mesa";
import { Panel } from "./Panel";
import type { Pestana } from "./Panel";
import { Tutorial } from "./Tutorial";

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
  usarIdioma();
  const cliente = useMemo(obtenerCliente, []);
  const estado: EstadoConexion = useSyncExternalStore(cliente.suscribir, cliente.obtener);
  const [panel, setPanel] = useState<Pestana | null>(null);
  const [tutorial, setTutorial] = useState(false);

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

  // Los navegadores solo habilitan audio después de un gesto del usuario.
  useEffect(() => {
    const desbloquear = () => desbloquearAudio();
    window.addEventListener("pointerdown", desbloquear, { once: true });
    window.addEventListener("keydown", desbloquear, { once: true });
    return () => {
      window.removeEventListener("pointerdown", desbloquear);
      window.removeEventListener("keydown", desbloquear);
    };
  }, []);

  // Errores de la app al log de diagnóstico (lo que no pasa por el WebSocket).
  useEffect(() => {
    const alError = (evento: ErrorEvent) => {
      registrar("!", "error-global", `${evento.message} @ ${evento.filename}:${evento.lineno}`);
    };
    const alRechazo = (evento: PromiseRejectionEvent) => {
      registrar("!", "promesa-rechazada", String(evento.reason));
    };
    window.addEventListener("error", alError);
    window.addEventListener("unhandledrejection", alRechazo);
    return () => {
      window.removeEventListener("error", alError);
      window.removeEventListener("unhandledrejection", alRechazo);
    };
  }, []);

  if (estado.motivo) return <Cerrado motivo={estado.motivo} />;

  const nombres = estado.vista
    ? estado.vista.asientos.map((asiento) => asiento.nombre)
    : (estado.lobby?.asientos.map((asiento) => asiento.nombre) ?? []);

  const panelCompartido = panel && (
    <Panel
      cliente={cliente}
      eventos={estado.eventos}
      nombres={nombres}
      pestanaInicial={panel}
      alCerrar={() => setPanel(null)}
      alVerTutorial={() => {
        setPanel(null);
        setTutorial(true);
      }}
    />
  );

  // Fuera de la mesa el tutorial se puede abrir desde Ajustes (lobby e ingreso).
  const tutorialCompartido = tutorial && (
    <Tutorial
      alCerrar={() => {
        cambiarPreferencias({ tutorialVisto: true });
        setTutorial(false);
      }}
    />
  );

  if (estado.miId === null) {
    return (
      <>
        <Ingreso
          conectado={estado.fase !== "iniciando" && estado.fase !== "cerrado"}
          nombreInicial={cliente.nombreGuardado()}
          aviso={estado.aviso}
          onEntrar={(nombre) => cliente.entrar(nombre)}
          onAyuda={() => setPanel("ayuda")}
          onAjustes={() => setPanel("ajustes")}
        />
        {panelCompartido}
        {tutorialCompartido}
      </>
    );
  }

  if (estado.vista) {
    return (
      <Mesa
        vista={estado.vista}
        cliente={cliente}
        eventos={estado.eventos}
        reconectando={estado.fase !== "online"}
      />
    );
  }

  if (estado.lobby) {
    return (
      <>
        <Lobby
          lobby={estado.lobby}
          miId={estado.miId}
          onListo={(listo) => cliente.listo(listo)}
          onAyuda={() => setPanel("ayuda")}
          onAjustes={() => setPanel("ajustes")}
        />
        {panelCompartido}
        {tutorialCompartido}
      </>
    );
  }

  return (
    <div className="pantalla centrada">
      <p className="cargando">{t("app_entrando")}</p>
    </div>
  );
}

function Cerrado({ motivo }: { motivo: string }) {
  usarIdioma();
  return (
    <div className="pantalla centrada">
      <div className="tarjeta">
        <h1>{t("app_cerrada_titulo")}</h1>
        <p>{motivo === "HOST_LEFT" ? t("app_host_salio") : motivo}</p>
        <button className="primario" onClick={() => window.location.reload()}>
          {t("app_volver")}
        </button>
      </div>
    </div>
  );
}

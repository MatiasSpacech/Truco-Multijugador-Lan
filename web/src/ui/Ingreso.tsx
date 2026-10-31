import { useState } from "react";
import { t, usarIdioma } from "../i18n";

interface Props {
  conectado: boolean;
  nombreInicial: string;
  aviso: string | null;
  onEntrar: (nombre: string) => void;
  onAyuda: () => void;
  onAjustes: () => void;
}

export function Ingreso({ conectado, nombreInicial, aviso, onEntrar, onAyuda, onAjustes }: Props) {
  usarIdioma();
  const [nombre, setNombre] = useState(nombreInicial);

  return (
    <div className="pantalla centrada">
      <form
        className="tarjeta ingreso"
        onSubmit={(evento) => {
          evento.preventDefault();
          if (nombre.trim()) onEntrar(nombre);
        }}
      >
        <h1>
          Truco <span className="dorado">Argentino</span>
        </h1>
        <p className="subtitulo">{t("ingreso_subtitulo")}</p>

        <label htmlFor="nombre">{t("ingreso_nombre")}</label>
        <input
          id="nombre"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
          maxLength={24}
          placeholder={t("ingreso_placeholder")}
          autoComplete="nickname"
          autoCapitalize="words"
          enterKeyHint="go"
        />

        <button type="submit" className="primario" disabled={!nombre.trim() || !conectado}>
          {conectado ? t("ingreso_entrar") : t("ingreso_conectando")}
        </button>

        {aviso && (
          <p className="aviso" role="alert">
            {aviso}
          </p>
        )}
        <p className="pie">{t("ingreso_pie")}</p>
      </form>

      <div className="fila">
        <button type="button" className="secundario chico" onClick={onAyuda}>
          {t("ingreso_como_jugar")}
        </button>
        <button type="button" className="secundario chico" onClick={onAjustes}>
          {t("ajustes")}
        </button>
      </div>
    </div>
  );
}

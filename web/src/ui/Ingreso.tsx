import { useState } from "react";

interface Props {
  conectado: boolean;
  nombreInicial: string;
  aviso: string | null;
  onEntrar: (nombre: string) => void;
}

export function Ingreso({ conectado, nombreInicial, aviso, onEntrar }: Props) {
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
        <p className="subtitulo">Multijugador en la red local</p>

        <label htmlFor="nombre">Tu nombre</label>
        <input
          id="nombre"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
          maxLength={24}
          placeholder="Ej.: Ana"
          autoComplete="nickname"
          autoCapitalize="words"
          enterKeyHint="go"
        />

        <button type="submit" className="primario" disabled={!nombre.trim() || !conectado}>
          {conectado ? "Entrar a la mesa" : "Conectando…"}
        </button>

        {aviso && <p className="aviso">{aviso}</p>}
        <p className="pie">
          2, 4 o 6 jugadores. Todos tienen que estar en la misma red Wi-Fi que el anfitrión.
        </p>
      </form>
    </div>
  );
}

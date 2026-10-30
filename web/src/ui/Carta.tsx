import { NOMBRE_PALO } from "../net/cliente";
import type { Carta as CartaDto } from "../tipos";
import { FIGURA, IconoPalo } from "./Palos";

interface Props {
  carta: CartaDto;
  jugable?: boolean;
  elegida?: boolean;
  chica?: boolean;
  onClick?: () => void;
}

export function Carta({ carta, jugable = false, elegida = false, chica = false, onClick }: Props) {
  const clases = [
    "carta",
    `palo-${carta.palo.toLowerCase()}`,
    jugable ? "jugable" : "",
    elegida ? "elegida" : "",
    chica ? "chica" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const figura = FIGURA[carta.numero];

  return (
    <button
      type="button"
      className={clases}
      onClick={onClick}
      disabled={!jugable}
      aria-label={`${carta.numero} de ${NOMBRE_PALO[carta.palo]}`}
    >
      <span className="carta-numero">{carta.numero}</span>
      <span className="carta-palo">
        <IconoPalo palo={carta.palo} />
      </span>
      {figura && <span className="carta-figura">{figura}</span>}
    </button>
  );
}

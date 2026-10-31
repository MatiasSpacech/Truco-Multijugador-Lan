import { nombreCarta, nombreFigura, usarIdioma } from "../i18n";
import type { Carta as CartaDto } from "../tipos";
import { IconoPalo } from "./Palos";

interface Props {
  carta: CartaDto;
  jugable?: boolean;
  elegida?: boolean;
  chica?: boolean;
  onClick?: () => void;
}

export function Carta({ carta, jugable = false, elegida = false, chica = false, onClick }: Props) {
  usarIdioma();
  const clases = [
    "carta",
    `palo-${carta.palo.toLowerCase()}`,
    jugable ? "jugable" : "",
    elegida ? "elegida" : "",
    chica ? "chica" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const figura = nombreFigura(carta.numero);

  return (
    <button
      type="button"
      className={clases}
      onClick={onClick}
      disabled={!jugable}
      aria-label={nombreCarta(carta)}
    >
      <span className="carta-numero">{carta.numero}</span>
      <span className="carta-palo">
        <IconoPalo palo={carta.palo} />
      </span>
      {figura && <span className="carta-figura">{figura}</span>}
    </button>
  );
}

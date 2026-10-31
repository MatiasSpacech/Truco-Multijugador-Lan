import { preferencias } from "./preferencias";

/**
 * Sonido y vibración sin archivos: todo se sintetiza con WebAudio.
 * Nada de assets remotos (el juego corre en una LAN sin internet) ni de licencias.
 */

export type Sonido = "reparto" | "carta" | "canto" | "punto" | "fin" | "turno" | "error";

type CtorAudio = typeof AudioContext;

let contexto: AudioContext | null = null;
let desbloqueado = false;

function obtenerContexto(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Ctor: CtorAudio | undefined =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: CtorAudio }).webkitAudioContext;
  if (!Ctor) return null;
  if (!contexto) {
    try {
      contexto = new Ctor();
    } catch {
      return null;
    }
  }
  if (contexto.state === "suspended") void contexto.resume();
  return contexto;
}

/**
 * Los navegadores (sobre todo iOS) solo dejan sonar audio después de un gesto.
 * Se llama en el primer toque de la app.
 */
export function desbloquearAudio(): void {
  const audio = obtenerContexto();
  if (!audio || desbloqueado) return;
  desbloqueado = true;
  const buffer = audio.createBuffer(1, 1, audio.sampleRate);
  const fuente = audio.createBufferSource();
  fuente.buffer = buffer;
  fuente.connect(audio.destination);
  fuente.start();
}

interface Nota {
  freq: number;
  /** Segundos desde ahora. */
  inicio?: number;
  dur?: number;
  tipo?: OscillatorType;
  volumen?: number;
  freqFin?: number;
}

function nota(audio: AudioContext, { freq, inicio = 0, dur = 0.12, tipo = "sine", volumen = 0.09, freqFin }: Nota): void {
  const arranque = audio.currentTime + inicio;
  const oscilador = audio.createOscillator();
  const ganancia = audio.createGain();
  oscilador.type = tipo;
  oscilador.frequency.setValueAtTime(freq, arranque);
  if (freqFin) oscilador.frequency.exponentialRampToValueAtTime(freqFin, arranque + dur);
  ganancia.gain.setValueAtTime(0.0001, arranque);
  ganancia.gain.exponentialRampToValueAtTime(volumen, arranque + 0.012);
  ganancia.gain.exponentialRampToValueAtTime(0.0001, arranque + dur);
  oscilador.connect(ganancia);
  ganancia.connect(audio.destination);
  oscilador.start(arranque);
  oscilador.stop(arranque + dur + 0.03);
}

/** Ruido filtrado: el "fru fru" de repartir cartas. */
function ruido(audio: AudioContext, { inicio = 0, dur = 0.14, volumen = 0.05, centro = 1900 }): void {
  const muestras = Math.max(1, Math.floor(audio.sampleRate * dur));
  const buffer = audio.createBuffer(1, muestras, audio.sampleRate);
  const datos = buffer.getChannelData(0);
  for (let i = 0; i < muestras; i += 1) {
    const caida = (1 - i / muestras) ** 2;
    datos[i] = (Math.random() * 2 - 1) * caida;
  }
  const fuente = audio.createBufferSource();
  fuente.buffer = buffer;
  const filtro = audio.createBiquadFilter();
  filtro.type = "bandpass";
  filtro.frequency.value = centro;
  filtro.Q.value = 0.8;
  const ganancia = audio.createGain();
  ganancia.gain.value = volumen;
  const arranque = audio.currentTime + inicio;
  fuente.connect(filtro);
  filtro.connect(ganancia);
  ganancia.connect(audio.destination);
  fuente.start(arranque);
}

const PARTITURAS: Record<Sonido, (audio: AudioContext) => void> = {
  reparto: (audio) => {
    ruido(audio, { inicio: 0, centro: 1500 });
    ruido(audio, { inicio: 0.09, centro: 2100 });
    ruido(audio, { inicio: 0.18, centro: 1700 });
  },
  carta: (audio) => {
    ruido(audio, { dur: 0.07, volumen: 0.045, centro: 2600 });
    nota(audio, { freq: 320, freqFin: 180, dur: 0.08, tipo: "triangle", volumen: 0.07 });
  },
  canto: (audio) => {
    nota(audio, { freq: 520, dur: 0.11, tipo: "triangle", volumen: 0.1 });
    nota(audio, { freq: 700, inicio: 0.1, dur: 0.14, tipo: "triangle", volumen: 0.1 });
  },
  punto: (audio) => {
    nota(audio, { freq: 660, dur: 0.1, volumen: 0.08 });
    nota(audio, { freq: 880, inicio: 0.09, dur: 0.14, volumen: 0.08 });
    nota(audio, { freq: 1046, inicio: 0.19, dur: 0.18, volumen: 0.07 });
  },
  fin: (audio) => {
    [523, 659, 784, 1046].forEach((freq, indice) => {
      nota(audio, { freq, inicio: indice * 0.11, dur: 0.22, tipo: "triangle", volumen: 0.09 });
    });
  },
  turno: (audio) => {
    nota(audio, { freq: 880, dur: 0.07, volumen: 0.05 });
  },
  error: (audio) => {
    nota(audio, { freq: 240, freqFin: 160, dur: 0.16, tipo: "sawtooth", volumen: 0.06 });
  },
};

export function sonar(sonido: Sonido, forzar = false): void {
  if (!forzar && !preferencias().sonido) return;
  const audio = obtenerContexto();
  if (!audio) return;
  try {
    PARTITURAS[sonido](audio);
  } catch {
    /* un navegador sin WebAudio no rompe la partida */
  }
}

/** Vibración corta; en navegadores sin soporte (iOS) no hace nada. */
export function vibrar(patron: number | number[] = 12): void {
  if (!preferencias().vibracion) return;
  if (typeof navigator === "undefined" || typeof navigator.vibrate !== "function") return;
  try {
    navigator.vibrate(patron);
  } catch {
    /* ignorado */
  }
}

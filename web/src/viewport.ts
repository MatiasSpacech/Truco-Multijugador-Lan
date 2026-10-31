/**
 * Alto real de la ventana como variable CSS.
 *
 * El WebView de Android (Chrome/156 en el emulador, y también en teléfonos viejos) resuelve
 * `100vh`/`100dvh` a **0**: con `max-height: 82vh` la hoja de ajustes quedaba en 0 px y no se veía
 * nada (bug reportado en la sala de espera de la app). `window.innerHeight` sí es correcto, así que
 * publicamos `--alto-visible` y el CSS calcula las alturas a partir de eso.
 *
 * Se actualiza al rotar y cuando aparece/desaparece el teclado (`visualViewport`).
 */
export function iniciarViewport(): void {
  if (typeof window === "undefined") return;

  const aplicar = () => {
    const alto = window.visualViewport?.height ?? window.innerHeight;
    if (!alto) return;
    document.documentElement.style.setProperty("--alto-visible", `${Math.round(alto)}px`);
  };

  aplicar();
  window.addEventListener("resize", aplicar);
  window.addEventListener("orientationchange", aplicar);
  window.visualViewport?.addEventListener("resize", aplicar);
}

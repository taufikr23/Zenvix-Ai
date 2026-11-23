import { useEffect, useRef, useState } from 'react';

export function PlanetScene() {
  const canvas = useRef(null), renderer = useRef(null);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    // The supplied logo remains visible while the lightweight renderer loads.
    import('../lib/planetRenderer').then(({ createPlanetRenderer }) => {
      if (!active || typeof WebGLRenderingContext === 'undefined') return;
      renderer.current = createPlanetRenderer(canvas.current, {
        onReady: () => { if (active) setReady(true); },
        onUnavailable: () => { if (active) setReady(false); },
      });
    }).catch(() => { if (active) setReady(false); });
    return () => { active = false; renderer.current?.dispose(); renderer.current = null; };
  }, []);
  return <div className={'planet-scene ' + (ready ? 'planet-ready' : '')}>
    <div className="planet-stage" aria-hidden="true"><canvas ref={canvas} className="planet-canvas"/></div>
  </div>;
}

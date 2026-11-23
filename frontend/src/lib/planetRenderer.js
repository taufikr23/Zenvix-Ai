// Analytic sphere and ring intersections give real depth without a model download.
const vertexSource = `
attribute vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }
`;

const fragmentSource = `
precision highp float;
uniform vec2 resolution;
uniform float time;
uniform vec2 pointer;

float hash(vec3 p) {
  p = fract(p * .3183099 + vec3(.1, .2, .3));
  p *= 17.0;
  return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}
float noise(vec3 p) {
  vec3 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(hash(i), hash(i+vec3(1,0,0)), f.x),
                 mix(hash(i+vec3(0,1,0)), hash(i+vec3(1,1,0)), f.x), f.y),
             mix(mix(hash(i+vec3(0,0,1)), hash(i+vec3(1,0,1)), f.x),
                 mix(hash(i+vec3(0,1,1)), hash(i+vec3(1,1,1)), f.x), f.y), f.z);
}
float cloud(vec3 p) {
  return .53*noise(p) + .27*noise(p*2.03) + .13*noise(p*4.07) + .07*noise(p*8.11);
}
vec2 sphereHit(vec3 ro, vec3 rd, float radius) {
  float b = dot(ro, rd), c = dot(ro, ro) - radius*radius;
  float d = b*b-c;
  if (d < 0.0) return vec2(-1.0);
  return vec2(-b-sqrt(d), -b+sqrt(d));
}
float ringDensity(float radius) {
  float edge = smoothstep(1.02, 1.05, radius) * (1.0-smoothstep(1.62, 1.65, radius));
  float bands = .68 + .15*sin(radius*190.0) + .10*sin(radius*341.0);
  float gap = 1.0 - .88*exp(-pow((radius-1.43)/.018, 2.0));
  return edge * bands * gap;
}
float star(vec2 uv, vec2 center, float size) {
  vec2 d = abs(uv-center);
  float core = exp(-length(d)*110.0/size);
  float cross = exp(-(d.x*15.0+d.y*240.0)/size) + exp(-(d.x*240.0+d.y*15.0)/size);
  return core + cross*.30;
}
void main() {
  // Fit the full ring into the enlarged hero, including on narrower laptops.
  float viewport = min(resolution.y, resolution.x/1.6);
  vec2 uv = (gl_FragCoord.xy-.5*resolution)/viewport*2.3;
  uv -= pointer * .045;
  vec3 ro = vec3(uv, 4.0), rd = vec3(0.0, 0.0, -1.0);
  vec3 axis = normalize(vec3(-.36, .82, .46));
  vec3 light = normalize(vec3(-3.0, 3.8, 4.0));
  float radius = .83;
  vec2 hit = sphereHit(ro, rd, radius);
  bool planet = hit.x > 0.0;
  vec3 color = vec3(0.0);
  float alpha = 0.0;

  // Atmosphere fades into actual transparency, without a rectangular backdrop.
  float limb = max(length(uv)-radius, 0.0);
  float halo = exp(-limb*25.0) * .22 * (1.0-smoothstep(1.0, 1.18, length(uv)));
  if (!planet) { alpha = halo; color = vec3(.03, .35, .95)*halo; }

  if (planet) {
    vec3 normal = normalize(ro + rd*hit.x);
    vec3 east = normalize(cross(vec3(0,0,1), axis));
    vec3 front = cross(east, axis);
    vec3 local = vec3(dot(normal, east), dot(normal, axis), dot(normal, front));
    float angle = time*.105;
    local.xz = mat2(cos(angle), -sin(angle), sin(angle), cos(angle))*local.xz;
    float turbulence = cloud(local*4.2 + vec3(0,0,time*.009));
    float latitude = local.y*25.0 + turbulence*4.6;
    float bands = .5+.5*sin(latitude);
    float detail = cloud(local*12.0 + vec3(turbulence*2.0));
    vec3 surface = mix(vec3(.025,.085,.35), vec3(.08,.40,.85), smoothstep(.12,.92,bands));
    surface = mix(surface, vec3(.28,.73,.94), smoothstep(.60,.83,detail)*.70);
    surface = mix(surface, vec3(.23,.16,.62), smoothstep(.4,.9,-local.y)*.25);
    surface *= .84 + .27*detail;
    float daylight = max(dot(normal, light), 0.0);
    // The ring casts a shadow on the spherical cloud surface.
    float shadowDistance = -dot(normal*radius, axis)/dot(light, axis);
    if (shadowDistance > .005) {
      float shadowRadius = length(normal*radius + light*shadowDistance);
      daylight *= 1.0 - .65*ringDensity(shadowRadius);
    }
    color = surface*(.10+daylight*1.15);
    float rim = pow(1.0-max(normal.z,0.0), 3.3);
    color += mix(vec3(.12,.10,.65), vec3(.10,.72,1.0), daylight)*rim*.7;
    color += vec3(.52,.83,1.0)*pow(max(dot(reflect(-light,normal),-rd),0.0),34.0)*.18;
    color = pow(color, vec3(.87));
    alpha = 1.0;
  }

  float ringHit = -dot(ro, axis)/dot(rd, axis);
  vec3 ringPoint = ro + rd*ringHit;
  float ringRadius = length(ringPoint);
  if (ringHit > 0.0 && (!planet || ringHit < hit.x) && ringRadius > 1.02 && ringRadius < 1.65) {
    float density = ringDensity(ringRadius);
    float azimuth = atan(ringPoint.z, ringPoint.x);
    density *= .94 + .06*sin(azimuth*12.0-time*.22+ringRadius*62.0);
    vec3 ringColor = mix(vec3(.20,.36,.87),vec3(.40,.83,1.0),smoothstep(1.05,1.32,ringRadius));
    ringColor = mix(ringColor, vec3(.60,.55,1.0), smoothstep(1.49,1.65,ringRadius)*.7);
    vec2 shadow = sphereHit(ringPoint+light*.01, light, radius);
    if (shadow.y > 0.0 && shadow.x > 0.0) ringColor *= .24;
    float edgeLight = exp(-pow((ringRadius-1.635)/.012,2.0));
    ringColor += vec3(.3,.55,.7)*edgeLight;
    float opacity = clamp(density*.93,0.0,1.0);
    color = ringColor*opacity + color*(1.0-opacity);
    alpha = opacity + alpha*(1.0-opacity);
  }

  float sparkle = star(uv, vec2(.94,1.02), .8) + star(uv, vec2(1.31,.66), .36);
  sparkle *= .88+.12*sin(time*.7);
  float starAlpha = clamp(sparkle,0.0,1.0);
  color = vec3(.69,.87,1.0)*starAlpha + color*(1.0-starAlpha);
  alpha = starAlpha+alpha*(1.0-starAlpha);
  gl_FragColor = vec4(color, alpha);
}
`;

export function createPlanetRenderer(canvas, { onReady, onUnavailable } = {}) {
  const gl = canvas.getContext('webgl', { alpha: true, antialias: false, depth: false, premultipliedAlpha: true, powerPreference: 'low-power' });
  if (!gl) throw new Error('WebGL unavailable');
  const shaders = []; let program, buffer, frame = 0, observer, disposed = false;
  let visible = true, elapsed = 0, previous = 0, lastDraw = -Infinity, ready = false;
  let aim = [0, 0], pointer = [0, 0];
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const locations = {};
  function stop() { cancelAnimationFrame(frame); frame = 0; previous = 0; }
  function dispose() {
    if (disposed) return;
    disposed = true; stop(); observer?.disconnect();
    window.removeEventListener('resize', resize);
    document.removeEventListener('visibilitychange', visibility);
    motion.removeEventListener('change', preference);
    canvas.removeEventListener('webglcontextlost', lost);
    canvas.removeEventListener('pointermove', move);
    canvas.removeEventListener('pointerleave', leave);
    if (buffer) gl.deleteBuffer(buffer);
    if (program) gl.deleteProgram(program);
    shaders.forEach(shader => gl.deleteShader(shader));
  }
  function canAnimate() { return !disposed && visible && !document.hidden && !motion.matches; }
  function draw() {
    if (disposed || !visible || document.hidden) return;
    const rect = canvas.getBoundingClientRect();
    if (rect.width < 1 || rect.height < 1) return;
    const scale = Math.min(window.devicePixelRatio || 1, 1.5, Math.sqrt(720000/(rect.width*rect.height)));
    const width = Math.max(1,Math.round(rect.width*scale)), height = Math.max(1,Math.round(rect.height*scale));
    if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
    gl.viewport(0,0,width,height);
    gl.useProgram(program);
    gl.uniform2f(locations.resolution,width,height);
    gl.uniform1f(locations.time,elapsed);
    gl.uniform2f(locations.pointer,pointer[0],pointer[1]);
    gl.drawArrays(gl.TRIANGLES,0,3);
    if (!ready) { ready = true; onReady?.(); }
  }
  function tick(now) {
    frame = 0;
    if (!canAnimate()) return;
    if (previous) elapsed += Math.min((now-previous)/1000,.1);
    previous = now;
    if (now-lastDraw >= 1000/30) {
      pointer = pointer.map((value,index) => value+(aim[index]-value)*.08);
      draw(); lastDraw = now;
    }
    frame = requestAnimationFrame(tick);
  }
  function sync() { stop(); draw(); if (canAnimate()) frame = requestAnimationFrame(tick); }
  function resize() { sync(); }
  function visibility() { sync(); }
  function preference() { aim = [0,0]; pointer = [0,0]; sync(); }
  function lost(event) { event.preventDefault(); dispose(); onUnavailable?.(); }
  function move(event) {
    if (!canAnimate()) return;
    const rect = canvas.getBoundingClientRect();
    aim = [(event.clientX-rect.left)/rect.width*2-1, 1-(event.clientY-rect.top)/rect.height*2];
  }
  function leave() { aim = [0,0]; }
  try {
    for (const [kind, source] of [[gl.VERTEX_SHADER,vertexSource],[gl.FRAGMENT_SHADER,fragmentSource]]) {
      const shader = gl.createShader(kind); shaders.push(shader);
      const precision = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER,gl.HIGH_FLOAT);
      gl.shaderSource(shader, kind === gl.FRAGMENT_SHADER && !precision?.precision ? source.replace('precision highp float','precision mediump float') : source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader,gl.COMPILE_STATUS)) throw new Error('Planet shader did not compile');
    }
    program = gl.createProgram(); shaders.forEach(shader => gl.attachShader(program,shader)); gl.linkProgram(program);
    if (!gl.getProgramParameter(program,gl.LINK_STATUS)) throw new Error('Planet renderer did not link');
    gl.useProgram(program);
    buffer = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
    gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
    const position = gl.getAttribLocation(program,'position');
    gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);
    for (const name of ['resolution','time','pointer']) locations[name] = gl.getUniformLocation(program,name);
    window.addEventListener('resize',resize);
    document.addEventListener('visibilitychange',visibility);
    motion.addEventListener('change',preference);
    canvas.addEventListener('webglcontextlost',lost);
    canvas.addEventListener('pointermove',move);
    canvas.addEventListener('pointerleave',leave);
    if (typeof IntersectionObserver !== 'undefined') {
      observer = new IntersectionObserver(entries => { visible = entries[0].isIntersecting; sync(); });
      observer.observe(canvas);
    }
    preference();
    return { dispose };
  } catch (error) { dispose(); throw error; }
}

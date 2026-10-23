// Omnitrix renderer: data + SVG builders shared by the prototype (index.html)
// and the watch-face asset exporter (assets.html). All geometry is in a 480×480 viewBox.
/* =========================================================
   DATA
   ========================================================= */
const THEMES = {
  green:  { label:'OMNITRIX',        c1:'#c8ff8a', c2:'#46e01e', c3:'#147a08', glow:'#4dff1f', ringA:'#3a403c', ringB:'#15181a', plate:'#0d110e', wedge:'#050805', ink:'#062a02', text:'#8aff52', slit:'#5dff2a' },
  master: { label:'MASTER CONTROL',  c1:'#fbffd0', c2:'#9dff2e', c3:'#0f9a3c', glow:'#c4ff3a', ringA:'#e6d389', ringB:'#5a4a1c', plate:'#0e100a', wedge:'#040604', ink:'#0a2a02', text:'#d6ff6a', slit:'#f4ff9a' },
  red:    { label:'RECHARGING',      c1:'#ff9a8a', c2:'#ff2a1e', c3:'#6e0000', glow:'#ff2a1a', ringA:'#3d2a2a', ringB:'#140c0c', plate:'#100909', wedge:'#070303', ink:'#2a0202', text:'#ff5a46', slit:'#ff3a2a' },
  alien:  { label:'ALIEN FORM',      c1:'#ffffff', c2:'#f1fff0', c3:'#b9dcb2', glow:'#e2ffd8', ringA:'#ffffff', ringB:'#9aa29a', plate:'#0b0d0b', wedge:'#060806', ink:'#0d3008', text:'#9bff66', slit:'#9bff66' },
};

// Emblems are original glyphs drawn in a 100×100 box (not official artwork).
// Each alien doubles as an app shortcut.
const ALIENS = [
  { name:'HEATBLAST',   fn:'Flashlight',     g:`<path d="M50 6C60 26 80 36 74 62C70 80 60 92 50 94C40 92 28 82 26 64C24 48 36 42 38 26C45 38 47 46 52 48C57 34 55 20 50 6Z"/><path d="M50 58C56 66 60 72 56 82C54 86 46 86 44 82C40 74 46 66 50 58Z" fill="#000" opacity=".55"/>` },
  { name:'XLR8',        fn:'Stopwatch',      g:`<path d="M10 26L38 50L10 74H24L52 50L24 26Z"/><path d="M42 26L70 50L42 74H56L84 50L56 26Z"/><rect x="84" y="44" width="10" height="12" rx="2"/>` },
  { name:'FOUR ARMS',   fn:'Workout',        g:`<circle cx="50" cy="20" r="11"/><rect x="36" y="34" width="28" height="44" rx="8"/><rect x="8" y="34" width="26" height="9" rx="4"/><rect x="66" y="34" width="26" height="9" rx="4"/><rect x="10" y="52" width="24" height="9" rx="4"/><rect x="66" y="52" width="24" height="9" rx="4"/><rect x="38" y="78" width="9" height="18" rx="3"/><rect x="53" y="78" width="9" height="18" rx="3"/>` },
  { name:'GREY MATTER', fn:'Calculator',     g:`<ellipse cx="50" cy="44" rx="34" ry="30"/><ellipse cx="36" cy="46" rx="9" ry="12" fill="#000"/><ellipse cx="64" cy="46" rx="9" ry="12" fill="#000"/><rect x="40" y="72" width="20" height="22" rx="6"/>` },
  { name:'DIAMONDHEAD', fn:'Brightness',     g:`<path d="M50 4L76 30L64 94H36L24 30Z"/><path d="M50 4L50 94M24 30H76M36 94L50 30L64 94" stroke="#000" stroke-width="3" fill="none" opacity=".55"/>` },
  { name:'RIPJAWS',     fn:'Water lock',     g:`<path d="M14 58C30 30 60 18 86 26C72 36 66 44 64 56C76 54 84 58 90 66C64 76 34 78 14 58Z"/><circle cx="72" cy="38" r="4" fill="#000"/><path d="M6 86C16 80 26 80 36 86S56 92 66 86S86 80 94 86" stroke="currentColor" stroke-width="5" fill="none"/>` },
  { name:'UPGRADE',     fn:'Settings',       g:`<circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="8"/><circle cx="50" cy="50" r="15"/><circle cx="50" cy="50" r="6" fill="#000"/><path d="M50 10V30M50 70V90M10 50H30M70 50H90" stroke="currentColor" stroke-width="5"/>` },
  { name:'GHOSTFREAK',  fn:'Do Not Disturb', g:`<path d="M20 92V44C20 22 34 8 50 8S80 22 80 44V92L70 82L60 92L50 82L40 92L30 82Z"/><circle cx="50" cy="40" r="13" fill="#000"/><circle cx="50" cy="40" r="5"/>` },
  { name:'STINKFLY',    fn:'Weather',        g:`<ellipse cx="50" cy="56" rx="10" ry="30"/><ellipse cx="24" cy="38" rx="20" ry="9" transform="rotate(-25 24 38)" opacity=".8"/><ellipse cx="76" cy="38" rx="20" ry="9" transform="rotate(25 76 38)" opacity=".8"/><ellipse cx="26" cy="62" rx="16" ry="7" transform="rotate(20 26 62)" opacity=".6"/><ellipse cx="74" cy="62" rx="16" ry="7" transform="rotate(-20 74 62)" opacity=".6"/><circle cx="40" cy="14" r="7"/><circle cx="60" cy="14" r="7"/>` },
  { name:'WILDMUTT',    fn:'Find my phone',  g:`<path d="M20 70C20 46 34 34 50 34S80 46 80 70L70 62L62 74L50 64L38 74L30 62Z"/><path d="M28 24C36 14 64 14 72 24M18 14C32 0 68 0 82 14" stroke="currentColor" stroke-width="5" fill="none"/>` },
  { name:'CANNONBOLT',  fn:'Steps',          g:`<circle cx="50" cy="50" r="42"/><path d="M14 40C34 30 66 30 86 40M10 58C32 50 68 50 90 58M30 14C26 38 26 62 30 86M70 14C74 38 74 62 70 86" stroke="#000" stroke-width="4" fill="none" opacity=".6"/>` },
  { name:'ECHO ECHO',   fn:'Music',          g:`<rect x="34" y="30" width="32" height="40" rx="10"/><circle cx="50" cy="50" r="7" fill="#000"/><path d="M24 36C18 44 18 56 24 64M76 36C82 44 82 56 76 64M14 28C4 42 4 58 14 72M86 28C96 42 96 58 86 72" stroke="currentColor" stroke-width="5" fill="none"/>` },
  { name:'CLOCKWORK',   fn:'Timer',          g:`<circle cx="50" cy="50" r="30" fill="none" stroke="currentColor" stroke-width="9"/><path d="M50 50V30M50 50L64 58" stroke="currentColor" stroke-width="7" stroke-linecap="round"/>${[0,45,90,135,180,225,270,315].map(a=>`<rect x="45" y="6" width="10" height="12" rx="2" transform="rotate(${a} 50 50)"/>`).join('')}` },
];

// Dossier shown on the hologram / info screen. Stats are fan ratings out of 10.
const DOSSIER = {
  'HEATBLAST':   { species:'Pyronite',            planet:'Pyros',          powers:['Pyrokinesis','Fire blasts','Flame-jet flight'],      weakness:'Water & extreme cold',   stats:[6,6,5,6] },
  'XLR8':        { species:'Kineceleran',         planet:'Kinet',          powers:['Super speed','Hyper reflexes','Visor shield'],       weakness:'Low-friction surfaces',  stats:[4,10,5,4] },
  'FOUR ARMS':   { species:'Tetramand',           planet:'Khoros',         powers:['Super strength','Shockwave clap','Huge leaps'],      weakness:'Big, slow target',       stats:[10,4,4,8] },
  'GREY MATTER': { species:'Galvan',              planet:'Galvan Prime',   powers:['Genius intellect','Tiny size','Tech know-how'],      weakness:'Physically fragile',     stats:[1,3,10,1] },
  'DIAMONDHEAD': { species:'Petrosapien',         planet:'Petropia',       powers:['Crystal shards','Diamond armor','Light refraction'], weakness:'Sonic vibrations',       stats:[7,4,5,10] },
  'RIPJAWS':     { species:'Piscciss Volann',     planet:'Piscciss',       powers:['Aquatic speed','Crushing bite','Deep-sea vision'],   weakness:'Dries out on land',      stats:[6,7,4,5] },
  'UPGRADE':     { species:'Galvanic Mechamorph', planet:'Galvan B',       powers:['Merges with tech','Upgrades machines','Shapeshift'], weakness:'Water & EMP',            stats:[3,5,8,4] },
  'GHOSTFREAK':  { species:'Ectonurite',          planet:'Anur Phaetos',   powers:['Intangibility','Invisibility','Possession'],        weakness:'Direct sunlight',        stats:[4,6,7,7] },
  'STINKFLY':    { species:'Lepidopterran',       planet:'Lepidopterra',   powers:['Flight','Slime spit','Stink spray'],                 weakness:'Wet wings',              stats:[3,7,4,3] },
  'WILDMUTT':    { species:'Vulpimancer',         planet:'Vulpin',         powers:['Super senses','Agility','Echolocation'],             weakness:'Loud noise, no eyes',    stats:[6,7,3,5] },
  'CANNONBOLT':  { species:'Arburian Pelarota',   planet:'Arburia',        powers:['Armored shell','Rolling charge','Ricochet'],         weakness:'Hard to steer',          stats:[7,8,3,9] },
  'ECHO ECHO':   { species:'Sonorosian',          planet:'Sonorosia',      powers:['Sonic scream','Self-cloning','Sound waves'],         weakness:'Fragile suit',           stats:[3,4,6,3] },
  'CLOCKWORK':   { species:'Chronosapien',        planet:'Unknown',        powers:['Time rays','Rewind','Glimpse the future'],           weakness:'Slow, heavy body',       stats:[5,2,8,7] },
};
const STAT_LABELS = ['STR','SPD','INT','DEF'];
ALIENS.forEach(a => { Object.assign(a, DOSSIER[a.name]); a.slug = a.name.toLowerCase().replace(/ /g, '-'); });

const HR_ZONES = [
  { max:99,  name:'GREY MATTER',  tag:'CALM'      },
  { max:119, name:'DIAMONDHEAD',  tag:'STEADY'    },
  { max:139, name:'FOUR ARMS',    tag:'STRENGTH'  },
  { max:159, name:'HEATBLAST',    tag:'BURN'      },
  { max:999, name:'XLR8',         tag:'MAX SPEED' },
];

/* =========================================================
   SVG HELPERS
   ========================================================= */
let UID = 0;
const pad = n => String(n).padStart(2,'0');
const DAYS = ['SUN','MON','TUE','WED','THU','FRI','SAT'];
const polar = (r, deg) => { const a=(deg-90)*Math.PI/180; return [240+r*Math.cos(a), 240+r*Math.sin(a)]; };
function arc(r, a0, a1) {
  const [x0,y0]=polar(r,a0), [x1,y1]=polar(r,a1);
  return `M${x0.toFixed(2)} ${y0.toFixed(2)}A${r} ${r} 0 ${a1-a0>180?1:0} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;
}
const emblem = (a, x, y, size, color, extra='') =>
  `<g transform="translate(${x-size/2} ${y-size/2}) scale(${size/100})" fill="${color}" color="${color}" ${extra}>${a.g}</g>`;
// Re-rendering every second would restart CSS animations; offsetting the delay by
// wall-clock time keeps looping animations continuous across renders.
const anim = (cls, period) => `class="${cls}" style="animation-delay:-${((performance.now()/1000) % period).toFixed(3)}s"`;
// One-shot animation that started at t0 (ms); stays finished on later renders.
const since = (cls, t0) => `class="${cls}" style="animation-delay:-${((performance.now() - t0)/1000).toFixed(3)}s"`;
// Face core radius (the selector keeps its own 150 px geometry).
const CORE_R = 218;   // no bezel, no seconds ring: the hourglass fills the screen
const themeFor = b => b > 90 ? 'master' : b < 20 ? 'red' : 'green';

function defs(t, id) {
  return `<defs>
    <radialGradient id="core${id}" cx="50%" cy="34%" r="70%">
      <stop offset="0" stop-color="${t.c1}"/><stop offset=".5" stop-color="${t.c2}"/><stop offset="1" stop-color="${t.c3}"/>
    </radialGradient>
    <radialGradient id="plate${id}" cx="50%" cy="30%" r="75%">
      <stop offset="0" stop-color="#2a302c"/><stop offset=".6" stop-color="${t.plate}"/><stop offset="1" stop-color="#000"/>
    </radialGradient>
    <linearGradient id="ring${id}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${t.ringA}"/><stop offset=".5" stop-color="${t.ringB}"/><stop offset="1" stop-color="${t.ringA}"/>
    </linearGradient>
    <clipPath id="clip${id}"><circle cx="240" cy="240" r="150"/></clipPath>
    <clipPath id="fclip${id}"><circle cx="240" cy="240" r="${CORE_R}"/></clipPath>
    <filter id="glow${id}" x="-50%" y="-50%" width="200%" height="200%">
      <feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="blur${id}"><feGaussianBlur stdDeviation="14"/></filter>
    <path id="arcB${id}" d="${arc(170, 120, 240).replace(/A/, 'A')}" />
    <path id="arcBot${id}" d="M${polar(CORE_R + 9,236)[0]} ${polar(CORE_R + 9,236)[1]}A${CORE_R + 9} ${CORE_R + 9} 0 0 0 ${polar(CORE_R + 9,124)[0]} ${polar(CORE_R + 9,124)[1]}"/>
    <path id="arcTop${id}" d="${arc(172, -62, 62)}"/>
  </defs>`;
}

// Background disc. No bezel, lights or seconds ring: the hourglass is the whole face.
function bezel(t, id, o) {
  return `<circle cx="240" cy="240" r="240" fill="#000"/>`;
}

function hourglass(t, id, o) {
  return `
    <circle cx="240" cy="240" r="${CORE_R + 16}" fill="url(#plate${id})" stroke="#000" stroke-width="3"/>
    ${[0,90,180,270].map(a=>`<rect x="236" y="${240 - CORE_R - 13}" width="8" height="10" rx="3" fill="#000" transform="rotate(${a} 240 240)" opacity=".8"/>`).join('')}
    <circle cx="240" cy="240" r="${CORE_R + 10}" fill="${t.glow}" opacity="${o.theme==='alien'?.35:.45}" filter="url(#blur${id})" ${o.theme==='red'?anim('breathe',3.2):''}/>
    <g ${o.theme==='red'?anim('flicker',4):''}>
      <circle cx="240" cy="240" r="${CORE_R}" fill="url(#core${id})"/>
      <g clip-path="url(#fclip${id})">
        ${o.theme==='master' ? `<rect x="200" y="40" width="60" height="400" fill="#fff" opacity=".35" ${anim('sheen',3.5)}/>` : ''}
        ${o.theme==='red' ? `<rect x="80" y="230" width="320" height="20" fill="#fff" opacity=".12" ${anim('scan',2.6)}/>` : ''}
        <polygon points="226,240 104,30 20,30 20,450 104,450" fill="${t.wedge}"/>
        <polygon points="254,240 376,30 460,30 460,450 376,450" fill="${t.wedge}"/>
      </g>
      <circle cx="240" cy="240" r="${CORE_R}" fill="none" stroke="#000" stroke-width="5"/>
      <circle cx="240" cy="240" r="${CORE_R - 4}" fill="none" stroke="#fff" stroke-width="1" opacity=".18"/>
    </g>`;
}

// Minimal face: the hourglass is the hero. Time is small, printed in ink in the top bulb.
function faceSVG(o) {
  const id = ++UID, t = THEMES[o.theme], d = o.time;
  const h = o.h24 ? d.getHours() : (d.getHours() % 12 || 12);
  const ink = (y, txt, size, weight = 900, spacing = 0, opacity = 1) =>
    `<text x="240" y="${y}" text-anchor="middle" font-family="Orbitron" font-weight="${weight}" font-size="${size}" letter-spacing="${spacing}" fill="${t.ink}" opacity="${opacity}">${txt}</text>`;
  const time = ink(141, `${pad(h)}:${pad(d.getMinutes())}`, 26);

  let content;
  if (o.theme === 'alien') {
    const a = ALIENS[o.alien];
    content = time + ink(166, a.name, a.name.length > 9 ? 10 : 11, 700, 1.5, .8) + emblem(a, 240, 306, 38, t.ink) +
      (o.countdown != null ? ink(370, `${pad(Math.floor(o.remaining/60))}:${pad(Math.floor(o.remaining%60))}`, 18) : '');
  } else {
    content = time + ink(166, `${DAYS[d.getDay()]} ${pad(d.getDate())}`, 11, 700, 3, .75);
  }

  const extras = [];
  if (o.theme === 'master') {
    // orbiting roster: every alien unlocked
    extras.push(`<g ${anim('spin-slow',40)} opacity=".9">${ALIENS.map((a,i)=>{const [x,y]=polar(CORE_R + 8, i*360/ALIENS.length); return emblem(a,x,y,10,t.slit);}).join('')}</g>`);
  }
  if (o.theme === 'alien' && o.countdown != null) {
    extras.push(`<path d="${arc(226, 0, Math.max(.5, o.countdown*359.9))}" fill="none" stroke="${t.text}" stroke-width="6" stroke-linecap="round" filter="url(#glow${id})"/>`);
  }
  const warn = o.theme === 'red'
    ? `<g class="blink"><text font-family="Orbitron" font-weight="700" font-size="11" letter-spacing="2" fill="${t.text}"><textPath href="#arcBot${id}" startOffset="50%" text-anchor="middle">▲ ENERGY LOW</textPath></text></g>`
    : '';

  return `<svg viewBox="0 0 480 480" xmlns="http://www.w3.org/2000/svg">
    ${defs(t,id)}
    ${bezel(t,id,o)}
    ${hourglass(t,id,o)}
    ${extras.join('')}${warn}
    ${content}
  </svg>`;
}

const STEP = 360 / ALIENS.length;
const IMG = {};   // slug -> URL of user-supplied artwork that loaded successfully

const HOLO_TINT = {
  green:  '.05 .1 .02 0 0  .35 .7 .15 0 .05  .12 .25 .05 0 .02  0 0 0 1 0',
  master: '.3 .6 .12 0 .04  .33 .66 .14 0 .05  .05 .1 .02 0 0  0 0 0 1 0',
  red:    '.25 .5 .1 0 .03  .02 .04 .01 0 0  .02 .04 .01 0 0  0 0 0 1 0',
};

// Filter, edge-fade mask, scanlines and projector cone for a hologram centred at (cx, cy).
function holoDefs(id, theme, col, cx, cy, r) {
  return `<defs>
    <filter id="holo${id}" x="-10%" y="-10%" width="120%" height="120%">
      <feColorMatrix type="matrix" values="${HOLO_TINT[theme]}" result="t"/>
      <feGaussianBlur in="t" stdDeviation="4" result="b"/>
      <feMerge><feMergeNode in="b"/><feMergeNode in="t"/></feMerge>
    </filter>
    <radialGradient id="hfade${id}"><stop offset=".6" stop-color="#fff"/><stop offset="1" stop-color="#000"/></radialGradient>
    <mask id="hmask${id}" maskUnits="userSpaceOnUse" x="0" y="0" width="480" height="480">
      <circle cx="${cx}" cy="${cy}" r="${r}" fill="url(#hfade${id})"/>
    </mask>
    <pattern id="scanl${id}" width="4" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="2" fill="#000" opacity=".45"/></pattern>
    <linearGradient id="cone${id}" x1="0" y1="1" x2="0" y2="0">
      <stop offset="0" stop-color="${col}" stop-opacity=".35"/><stop offset="1" stop-color="${col}" stop-opacity="0"/>
    </linearGradient>
  </defs>`;
}

// The alien itself: real artwork if the user supplied it, otherwise the original glyph.
function holoArt(a, id, cx, cy, size, col) {
  const art = IMG[a.slug]
    ? `<image href="${IMG[a.slug]}" x="${cx-size/2}" y="${cy-size/2}" width="${size}" height="${size}" preserveAspectRatio="xMidYMid meet" filter="url(#holo${id})"/>`
    : emblem(a, cx, cy, size*.72, col, `filter="url(#glow${id})"`);
  return `<g mask="url(#hmask${id})">
    ${art}
    <rect x="${cx-size/2}" y="${cy-size/2}" width="${size}" height="${size}" fill="url(#scanl${id})"/>
    <rect x="${cx-size/2}" y="${cy}" width="${size}" height="3" fill="${col}" opacity=".7" ${anim('scan',2.6)}/>
  </g>`;
}

function statBars(stats, col, x0, y, gap=47) {
  return stats.map((v,k) => {
    const x = x0 + k*gap;
    return `<text x="${x+19}" y="${y}" text-anchor="middle" font-family="Orbitron" font-size="8" letter-spacing="1" fill="#6f9a66">${STAT_LABELS[k]}</text>` +
      Array.from({length:10}, (_,n) => `<rect x="${x+n*4}" y="${y+4}" width="3" height="6" fill="${n<v?col:'#1c2a1a'}"/>`).join('');
  }).join('');
}

function selectorSVG(o) {
  const id = ++UID, th = o.theme === 'red' ? 'red' : o.theme === 'master' ? 'master' : 'green', t = THEMES[th];
  const a = ALIENS[o.alien], locked = th === 'red';
  const col = locked ? '#ff4a3a' : t.text;
  const rot = o.ringRot ?? -o.alien * STEP;
  // DNA carousel around the bezel; whatever sits at 12 o'clock is selected
  let ring = '';
  ALIENS.forEach((al, i) => {
    const ang = ((i*STEP + rot) % 360 + 540) % 360 - 180;
    const sel = i === o.alien;
    const [x,y] = polar(206, ang);
    const op = sel ? 1 : Math.max(.22, 1 - Math.abs(ang)/190);
    ring += `<g opacity="${op.toFixed(2)}">
      <circle cx="${x}" cy="${y}" r="${sel?25:17}" fill="#000" stroke="${sel?col:'#2a3a28'}" stroke-width="${sel?3:2}" ${sel?`filter="url(#glow${id})"`:''}/>
      ${emblem(al, x, y, sel?30:20, sel ? col : locked ? '#5a2a24' : '#4c6e44')}
    </g>`;
  });
  return `<svg viewBox="0 0 480 480">
    ${defs(t,id)}
    ${holoDefs(id, th, col, 240, 196, 92)}
    <circle cx="240" cy="240" r="240" fill="#000"/>
    <circle cx="240" cy="240" r="226" fill="none" stroke="url(#ring${id})" stroke-width="26" opacity=".45"/>
    ${ring}
    <polygon points="231,0 249,0 240,7" fill="${col}"/>
    <g ${since('pop', o.enteredAt ?? -1e9)}>
      <circle cx="240" cy="240" r="176" fill="#000" stroke="${col}" stroke-width="2" opacity=".95"/>
      <circle cx="240" cy="240" r="168" fill="none" stroke="${col}" stroke-width="1" stroke-dasharray="2 6" opacity=".5" ${anim('spin-rev',60)}/>
      <text x="240" y="88" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="13" fill="${locked?'#a5554a':'#6f9a66'}">${pad(o.time.getHours()%12||12)}:${pad(o.time.getMinutes())} · DNA ${pad(o.alien+1)}/${pad(ALIENS.length)}</text>
      <polygon points="198,292 282,292 334,104 146,104" fill="url(#cone${id})"/>
      <ellipse cx="240" cy="292" rx="44" ry="7" fill="${col}" opacity=".7" filter="url(#glow${id})"/>
      <g ${since('holoIn', o.changedAt ?? -1e9)}><g ${anim('holo',2.2)} opacity="${locked?.45:1}">${holoArt(a, id, 240, 196, 184, col)}</g></g>
      <text x="240" y="326" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="25" letter-spacing="3" fill="${col}" filter="url(#glow${id})">${a.name}</text>
      <text x="240" y="345" text-anchor="middle" font-family="Chakra Petch" font-weight="600" font-size="13" letter-spacing="2" fill="${locked?'#c07468':'#8fbf84'}">${a.species.toUpperCase()} · ${a.planet.toUpperCase()}</text>
      ${statBars(a.stats, col, 150, 362)}
      <text x="240" y="398" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="10" letter-spacing="3" fill="${col}" ${anim('breathe',3.2)}>${locked ? 'NO ENERGY · CHARGE' : 'TAP TO TRANSFORM'}</text>
    </g>
  </svg>`;
}

// Full alien profile: swipe up on the hologram to open, tap to return.
function dossierSVG(o) {
  const id = ++UID, th = o.theme === 'red' ? 'red' : o.theme === 'master' ? 'master' : 'green', t = THEMES[th];
  const a = ALIENS[o.alien], col = th === 'red' ? '#ff4a3a' : t.text, dim = th === 'red' ? '#a5554a' : '#6f9a66';
  // stat arcs in the four bezel quadrants
  const arcs = a.stats.map((v,k) => {
    const c = [315,45,225,135][k], a0 = c-28, a1 = c+28, fill = a0 + (a1-a0)*v/10;
    const [lx,ly] = polar(192, c);
    return `<path d="${arc(218,a0,a1)}" stroke="#16231a" stroke-width="10" fill="none" stroke-linecap="round"/>
      <path d="${arc(218,a0,Math.max(a0+.5,fill))}" stroke="${col}" stroke-width="10" fill="none" stroke-linecap="round" filter="url(#glow${id})"/>
      <text x="${lx}" y="${ly-2}" text-anchor="middle" font-family="Orbitron" font-size="9" letter-spacing="1" fill="${dim}">${STAT_LABELS[k]}</text>
      <text x="${lx}" y="${ly+13}" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="14" fill="${col}">${v}</text>`;
  }).join('');
  const label = (x,y,txt) => `<text x="${x}" y="${y}" text-anchor="middle" font-family="Orbitron" font-size="9" letter-spacing="3" fill="${dim}">${txt}</text>`;
  const value = (x,y,txt,size=13) => `<text x="${x}" y="${y}" text-anchor="middle" font-family="Chakra Petch" font-weight="700" font-size="${size}" letter-spacing="1" fill="#dff5d6">${txt}</text>`;
  return `<svg viewBox="0 0 480 480">
    ${defs(t,id)}
    ${holoDefs(id, th, col, 240, 124, 66)}
    <circle cx="240" cy="240" r="240" fill="#000"/>
    ${arcs}
    <g ${since('pop', o.enteredAt ?? -1e9)}>
      <polygon points="212,196 268,196 304,62 176,62" fill="url(#cone${id})"/>
      <ellipse cx="240" cy="196" rx="30" ry="5" fill="${col}" opacity=".7" filter="url(#glow${id})"/>
      <g ${anim('holo',2.2)}>${holoArt(a, id, 240, 124, 132, col)}</g>
      <text x="240" y="232" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="26" letter-spacing="3" fill="${col}" filter="url(#glow${id})">${a.name}</text>
      ${label(160,256,'SPECIES')}${value(160,273,a.species.toUpperCase(),a.species.length>14?10.5:12)}
      ${label(320,256,'HOME WORLD')}${value(320,273,a.planet.toUpperCase(),a.planet.length>14?10.5:12)}
      <line x1="240" y1="250" x2="240" y2="278" stroke="#1f3320"/>
      ${label(240,302,'POWERS')}${value(240,320,a.powers.join(' · '),a.powers.join(' · ').length>40?11.5:13)}
      ${label(240,346,'WEAKNESS')}${value(240,363,a.weakness.toUpperCase(),12)}
      <rect x="150" y="377" width="180" height="24" rx="12" fill="none" stroke="${col}" stroke-width="1.5"/>
      <text x="240" y="393" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="10" letter-spacing="2" fill="${col}">◆ ${a.fn.toUpperCase()}</text>
      <text x="240" y="424" text-anchor="middle" font-family="Orbitron" font-size="8" letter-spacing="3" fill="${dim}">TAP TO RETURN</text>
    </g>
  </svg>`;
}

function hrSVG(o) {
  const id = ++UID, t = THEMES.green;
  const z = HR_ZONES.find(z => o.hr <= z.max);
  const zi = HR_ZONES.indexOf(z);
  const beat = 'l18 0 l6 -6 l6 6 l8 0 l4 8 l6 -54 l6 62 l4 -16 l10 0 l8 -12 l8 12 l36 0';
  let ecg = 'M0 0'; for (let i = 0; i < 6; i++) ecg += beat;
  // gauge 40..200 bpm mapped to -130..130 deg
  const ang = v => -130 + (Math.min(200, Math.max(40, v)) - 40) / 160 * 260;
  const zoneCols = ['#2f8f2a','#4fcf2a','#9dff2e','#ffb02e','#ff4a2e'];
  const bounds = [40,100,120,140,160,200];
  let gauge = '';
  for (let i = 0; i < 5; i++) gauge += `<path d="${arc(212, ang(bounds[i])+1, ang(bounds[i+1])-1)}" stroke="${zoneCols[i]}" stroke-width="${i===zi?16:9}" fill="none" opacity="${i===zi?1:.45}" ${i===zi?`filter="url(#glow${id})"`:''}/>`;
  const [nx,ny] = polar(212, ang(o.hr));
  const beatDur = (60 / o.hr).toFixed(2);
  return `<svg viewBox="0 0 480 480">
    ${defs(t,id)}
    <clipPath id="ecgc${id}"><circle cx="240" cy="240" r="186"/></clipPath>
    <circle cx="240" cy="240" r="240" fill="#000"/>
    ${gauge}
    <circle cx="${nx}" cy="${ny}" r="11" fill="#fff" stroke="#000" stroke-width="3"/>
    <text x="${polar(184,-128)[0]}" y="${polar(184,-128)[1]}" font-family="Orbitron" font-size="11" fill="#4f7a45" text-anchor="middle">40</text>
    <text x="${polar(184,128)[0]}" y="${polar(184,128)[1]}" font-family="Orbitron" font-size="11" fill="#4f7a45" text-anchor="middle">200</text>
    <!-- DNA helix backdrop -->
    <g opacity=".22" clip-path="url(#ecgc${id})">
      ${Array.from({length:22},(_,i)=>{const y=40+i*19, s=Math.sin(i*.6)*90; return `<line x1="${240-s}" y1="${y}" x2="${240+s}" y2="${y}" stroke="${t.text}" stroke-width="2"/><circle cx="${240-s}" cy="${y}" r="4" fill="${t.text}"/><circle cx="${240+s}" cy="${y}" r="4" fill="${t.text}"/>`;}).join('')}
    </g>
    <!-- pulsing omnitrix core as the heart -->
    <g style="transform-origin:240px 118px;animation:hrp${id} ${beatDur}s ease-out infinite;animation-delay:-${((performance.now()/1000)%beatDur).toFixed(3)}s">
      <circle cx="240" cy="118" r="34" fill="${t.glow}" opacity=".35" filter="url(#blur${id})"/>
      <circle cx="240" cy="118" r="28" fill="url(#core${id})" stroke="#000" stroke-width="3"/>
      <polygon points="237,118 222,90 212,90 212,146 222,146" fill="#050805"/>
      <polygon points="243,118 258,90 268,90 268,146 258,146" fill="#050805"/>
    </g>
    <style>@keyframes hrp${id}{0%{transform:scale(1)}12%{transform:scale(1.14)}30%{transform:scale(1)}}</style>
    <text x="240" y="176" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="12" letter-spacing="5" fill="#6f9a66">BIO-SCAN</text>
    <g clip-path="url(#ecgc${id})">
      <g transform="translate(0 262)"><path d="${ecg}" ${anim('ecg',2.4)} fill="none" stroke="${t.text}" stroke-width="3" stroke-linejoin="round" filter="url(#glow${id})"/></g>
    </g>
    <text x="240" y="236" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="74" fill="#eaffe0" filter="url(#glow${id})">${o.hr}</text>
    <text x="240" y="316" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="11" letter-spacing="4" fill="#6f9a66">ZONE ${zi+1} · ${z.tag}</text>
    ${emblem(ALIENS.find(a=>a.name===z.name), 196, 350, 30, zoneCols[zi])}
    <text x="216" y="356" font-family="Orbitron" font-weight="900" font-size="15" letter-spacing="2" fill="${zoneCols[zi]}">${z.name}</text>
    <text x="150" y="398" text-anchor="middle" font-family="Orbitron" font-size="10" fill="#4f7a45">MIN</text>
    <text x="150" y="414" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="15" fill="#9dff7a">54</text>
    <text x="240" y="398" text-anchor="middle" font-family="Orbitron" font-size="10" fill="#4f7a45">REST</text>
    <text x="240" y="414" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="15" fill="#9dff7a">61</text>
    <text x="330" y="398" text-anchor="middle" font-family="Orbitron" font-size="10" fill="#4f7a45">MAX</text>
    <text x="330" y="414" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="15" fill="#9dff7a">148</text>
  </svg>`;
}

// Always-on art: a "powered-down" Omnitrix. Budget: ≤ 15% lit pixels (Wear OS AOD guidance);
// measured by tools/build_watchface.py. Thin neon lines, dimly glowing bulbs,
// everything else dark. Static (no text) so the watch face can reuse it as a layer.
const AOD = {
  green: { line:'#46C424', dim:'#0E3A08', faint:'#163A14', label:'#2A6A1A' },
  alien: { line:'#D6F5CC', dim:'#26332A', faint:'#202A22', label:'#7FA878' },
};
function ambientArt(id, mode='green') {
  const c = AOD[mode];
  const bulb = (y0) => `<linearGradient id="bulb${id}${y0}" x1="0" y1="${y0 < 240 ? 0 : 1}" x2="0" y2="${y0 < 240 ? 1 : 0}">
      <stop offset="0" stop-color="${c.dim}"/><stop offset=".2" stop-color="${c.dim}" stop-opacity="0"/></linearGradient>`;
  return `<defs>
      ${bulb(90)}${bulb(390)}
      <filter id="aglow${id}" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="1.5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
      <clipPath id="aclip${id}"><circle cx="240" cy="240" r="${CORE_R}"/></clipPath>
      <path id="aarc${id}" d="${arc(172, -62, 62)}"/>
    </defs>
    <circle cx="240" cy="240" r="${CORE_R + 16}" fill="none" stroke="${c.faint}" stroke-width="1.5"/>
    <g clip-path="url(#aclip${id})">
      <polygon points="226,240 104,30 376,30 254,240" fill="url(#bulb${id}90)"/>
      <polygon points="226,240 104,450 376,450 254,240" fill="url(#bulb${id}390)"/>
    </g>
    <g filter="url(#aglow${id})" fill="none" stroke="${c.line}" stroke-width="2.2" stroke-linejoin="round">
      <circle cx="240" cy="240" r="${CORE_R}"/>
      <g clip-path="url(#aclip${id})">
        <polyline points="98,20 226,240 98,460"/>
        <polyline points="382,20 254,240 382,460"/>
      </g>
    </g>
    `;
}

function ambientSVG(o) {
  const id = ++UID, c = AOD.green;
  const h = o.time.getHours() % 12 || 12;
  return `<svg viewBox="0 0 480 480">
    <rect width="480" height="480" fill="#000"/>
    ${ambientArt(id, 'green')}
    <text x="240" y="142" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="30" fill="${c.dim}" stroke="${c.line}" stroke-width="1.2">${pad(h)}:${pad(o.time.getMinutes())}</text>
    <text x="240" y="166" text-anchor="middle" font-family="Orbitron" font-weight="700" font-size="11" letter-spacing="3" fill="${c.label}">${DAYS[o.time.getDay()]} ${pad(o.time.getDate())}</text>
  </svg>`;
}

function flashSVG(color, label) {
  return `<svg viewBox="0 0 480 480">
    <defs><radialGradient id="fl"><stop offset="0" stop-color="#fff"/><stop offset=".35" stop-color="${color}"/><stop offset="1" stop-color="#000"/></radialGradient></defs>
    <circle cx="240" cy="240" r="240" fill="url(#fl)"/>
    ${label ? `<text x="240" y="420" text-anchor="middle" font-family="Orbitron" font-weight="900" font-size="18" letter-spacing="4" fill="#000">${label}</text>` : ''}
  </svg>`;
}

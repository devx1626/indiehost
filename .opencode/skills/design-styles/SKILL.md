---
name: design-styles
description: Catalog of 67 visual design styles (tokens, palette, typography, layout and implementation guidance) for choosing or applying a visual direction. Use when asked for a redesign, theme, mood, visual style, colour system or UI direction, or when comparing design options. Each style is a local reference, not a separate skill.
license: MIT
---

# Design style catalog

Local catalog of the MIT-licensed style guides from
[bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills).
The upstream repository had 67 styles at commit `f631a09b4fcc0166f2e2c1a8c81906ef680c57e8`.

Each style directory contains:

- `references/<style>/DESIGN.md` — palette, typography, spacing, surfaces and motion tokens.
- `references/<style>/STYLE.md` — the upstream implementation guidance.

## How to use this catalog

1. Pick **one** style that fits the brief. Do not mix several unless the user asks for a fusion.
2. Read `DESIGN.md` first; it is the compact token sheet.
3. Read `STYLE.md` only for the implementation details you need.
4. Translate web CSS into Compose/Material 3 tokens. Do not paste CSS or web layout assumptions into Android code.
5. Keep IndieHost’s existing identity unless the user explicitly asks for a redesign. A style is a reference, not a mandate.
6. Check contrast, touch targets, font scaling and dark-mode behaviour before proposing the result.

## Shortlist for IndieHost

| Style | Why it may fit |
| --- | --- |
| [material](references/material/DESIGN.md) | Android baseline; already installed separately |
| [minimal](references/minimal/DESIGN.md) | calm, low-noise home screen |
| [retro](references/retro/DESIGN.md) | game-history flavour without copying a game |
| [neon](references/neon/DESIGN.md) | high-contrast arcade energy |
| [cosmic](references/cosmic/DESIGN.md) | dark sci-fi library |
| [premium](references/premium/DESIGN.md) | polished, restrained console feel |
| [editorial](references/editorial/DESIGN.md) | strong type-led library |
| [futuristic](references/futuristic/DESIGN.md) | tech-forward shell |
| [immersive](references/immersive/DESIGN.md) | game-like discovery |
| [sega](references/sega/DESIGN.md) | arcade-inspired, playful |
| [pacman](references/pacman/DESIGN.md) | 8-bit arcade nostalgia |
| [fantasy](references/fantasy/DESIGN.md) | premium game-world atmosphere |

## All styles

| Style | Description |
| --- | --- |
| [agentic](references/agentic/DESIGN.md) | Conversational AI-first interface with minimal controls, clear outcomes, and delegated task flows for agentic workflows. |
| [ant](references/ant/DESIGN.md) | Structured, enterprise-focused design system emphasizing clarity, consistency, and efficiency for data-dense web applications. |
| [artistic](references/artistic/DESIGN.md) | High-contrast, expressive style with creative typography and bold color choices for visually striking interfaces. |
| [basic](references/basic/DESIGN.md) | Print-inspired visual language for books, magazines, and reports with editorial grids and expressive typography. |
| [bento](references/bento/DESIGN.md) | Modular grid layout with card-like blocks, clear hierarchy, soft spacing, and subtle visual contrast for organized, scannable interfaces. |
| [bold](references/bold/DESIGN.md) | Strong visual presence with heavyweight typography, high-contrast colors, and commanding layouts. |
| [brutalism](references/brutalism/DESIGN.md) | Raw, anti-design aesthetic inspired by concrete architecture with unadorned elements, jarring layouts, and functional minimalism. |
| [cafe](references/cafe/DESIGN.md) | Cozy cafe-inspired interface with warm tones, soft typography, and clean layouts for a relaxed browsing experience. |
| [claude](references/claude/DESIGN.md) | A research-journal aesthetic printed on warm stone — authoritative, editorial, almost achromatic. Pages live on warm ivory parchment (never pure white), with near-black slate as the dominant ink. |
| [claymorphism](references/claymorphism/DESIGN.md) | Soft, rounded 3D-like shapes mimicking malleable clay with playful, puffy elements and colorful surfaces. |
| [clean](references/clean/DESIGN.md) | Simplicity-focused design with ample whitespace, legible typography, and a limited color palette to reduce visual clutter. |
| [codex](references/codex/DESIGN.md) | A radically minimal, blank-canvas interface built as a pure edge-to-edge surface, with almost no color and typography carrying the visual weight. Black serves as the only filled color, the only divider, and the sole surface tone cards. |
| [colorful](references/colorful/DESIGN.md) | Vibrant, high-contrast palettes and gradients for engaging, memorable, and modern user experiences. |
| [contemporary](references/contemporary/DESIGN.md) | Current-era minimalist design with bento grids, dark mode support, and high-performance accessible layouts. |
| [corporate](references/corporate/DESIGN.md) | Professional, brand-aligned design with structured grids, minimalist layouts, and consistent enterprise patterns. |
| [cosmic](references/cosmic/DESIGN.md) | Futuristic sci-fi aesthetic with dark themes, vibrant neon accents, and immersive spatial elements. |
| [creative](references/creative/DESIGN.md) | Playful, character-driven design with expressive typography and bold graphics for landing pages and creative projects. |
| [dithered](references/dithered/DESIGN.md) | Dot-pattern rendering technique that simulates shades with a limited palette for nostalgic, retro, high-contrast visuals. |
| [doodle](references/doodle/DESIGN.md) | Hand-drawn, sketch-like style with doodles, handwritten fonts, and imperfect lines for a playful, informal feel. |
| [dramatic](references/dramatic/DESIGN.md) | High-contrast, theatrical design with bold layouts, immersive visuals, and unconventional compositions that command attention. |
| [editorial](references/editorial/DESIGN.md) | Magazine-inspired editorial layout with refined serif typography, structured grids, and elegant reading experiences. |
| [enterprise](references/enterprise/DESIGN.md) | Dark-themed cloud-platform aesthetic with modular grids, glass-like panels, and strong data hierarchy for productivity dashboards. |
| [expressive](references/expressive/DESIGN.md) | Vibrant, personality-driven design with bold colors, playful graphics, and dynamic layouts that balance creativity with structure. |
| [fantasy](references/fantasy/DESIGN.md) | Game-inspired fantasy aesthetic with bold, premium visuals, rich color palettes, and immersive thematic elements. |
| [fiction](references/fiction/DESIGN.md) | A playful, energetic, cartoonesque interface inspired by friendly children's-book illustrations — warm cream backgrounds, big bold custom display typography, saturated brand color blocks, thick black outlines, generously rounded shapes |
| [flat](references/flat/DESIGN.md) | Two-dimensional minimalist style with vibrant colors, clean typography, and no 3D effects for fast, user-friendly interfaces. |
| [friendly](references/friendly/DESIGN.md) | Approachable, intuitive design with rounded elements, ample whitespace, and soft pastel color palettes. |
| [futuristic](references/futuristic/DESIGN.md) | Forward-looking design with tech-inspired typography, modern layouts, and a sleek, innovation-driven aesthetic. |
| [geometric](references/geometric/DESIGN.md) | Geometric, structured design with clean typography, neutral colors, precise shapes, and intuitive layouts that stay out of the way. |
| [glassmorphism](references/glassmorphism/DESIGN.md) | Frosted glass effect with translucent layers, subtle blur, and luminous borders for depth and modern elegance. |
| [gradient](references/gradient/DESIGN.md) | Smooth color transitions and gradient-rich surfaces for modern, playful interfaces with visual depth. |
| [immersive](references/immersive/DESIGN.md) | An immersive, interactive, exhibit-style interface that blends storytelling, animation, and gamified elements to create a playful, experience-driven journey. The entire app sits on a single continuous brand-colored canvas (deep green) |
| [impeccable](references/impeccable/DESIGN.md) | A modern, graphic, editorial-poster aesthetic — warm and confident — built on alternating cream and burnt orange sections, an amber brand color. |
| [levels](references/levels/DESIGN.md) | Conversion-focused design that removes friction and guides users toward action through clarity, trust, and speed. |
| [lingo](references/lingo/DESIGN.md) | Playful, minimal design with bright colors, rounded shapes, tactile 3D borders, and friendly illustrations for approachable interfaces. |
| [material](references/material/DESIGN.md) | Google's Material Design with layered surfaces, dynamic theming, built-in motion, and responsive cross-platform patterns. |
| [matrix](references/matrix/DESIGN.md) | A cyber-slick, dark-only Matrix-inspired interface defined by minimalist fashion, high-tech digital elements |
| [minimal](references/minimal/DESIGN.md) | Stripped-back design emphasizing whitespace, clean typography, and restrained color for maximum clarity and focus. |
| [modern](references/modern/DESIGN.md) | Contemporary editorial style with serif typography, minimal palettes, and clean layouts for polished digital products. |
| [mono](references/mono/DESIGN.md) | Monospace-driven, matrix-inspired design with high-contrast elements, compact density, and a hacker-chic aesthetic. |
| [neobrutalism](references/neobrutalism/DESIGN.md) | Modern take on brutalism with bold borders, vivid accent colors, and raw, high-contrast layouts on warm surfaces. |
| [neon](references/neon/DESIGN.md) | Electric neon glow effects with high-contrast color pairings for bold, attention-grabbing interfaces. |
| [neumorphism](references/neumorphism/DESIGN.md) | Soft, extruded UI elements with inner and outer shadows on monochromatic surfaces for a tactile, embedded look. |
| [pacman](references/pacman/DESIGN.md) | Retro arcade-inspired design with pixel fonts, dotted borders, playful high-contrast colors, and 8-bit game aesthetics. |
| [paper](references/paper/DESIGN.md) | Paper-textured, print-inspired design with minimal colors, clean serif/sans typography, and tactile surface qualities. |
| [perspective](references/perspective/DESIGN.md) | Spatial depth design with isometric views, vanishing points, and layered elements that guide attention through 3D-like realism. |
| [power](references/power/DESIGN.md) | High-end dark aesthetic with bold headings, monochromatic palette, and premium feel for premium brand experiences. |
| [premium](references/premium/DESIGN.md) | Apple-inspired premium aesthetic with precise spacing, modern typography, and a refined, polished visual language. |
| [professional](references/professional/DESIGN.md) | Polished, business-ready design with modern typography, structured layouts, and a trustworthy visual identity. |
| [pulse](references/pulse/DESIGN.md) | Dynamic, vibrant style with thick borders, geometric shapes, high-contrast colors, and expressive typography conveying motion and vitality. |
| [refined](references/refined/DESIGN.md) | Carefully curated, modern minimal style with elegant serif typography and understated, sophisticated palettes. |
| [retro](references/retro/DESIGN.md) | Throwback design with vintage-inspired typography, high-contrast retro palettes, and nostalgic visual elements. |
| [riso](references/riso/DESIGN.md) | A playful, joyful, two-color risograph print aesthetic built on a single warm off-white paper surface running through every section |
| [roku](references/roku/DESIGN.md) | App dashboard with purple-themed aesthetic, top-bar navigation, card-based layouts, and developer-first workflows. |
| [sega](references/sega/DESIGN.md) | A playful, arcade-inspired interface for games — built on the VT323 pixel typeface, hard-edged 0px corners, chunky pill buttons that physically press into solid offset blocks |
| [shadcn](references/shadcn/DESIGN.md) | Shadcn/ui-inspired design with minimal, clean components, monochrome palette, and utility-first patterns. |
| [sketch](references/sketch/DESIGN.md) | A friendly, hand-drawn sketch interface inspired by pencil illustrations on warm cream paper. Soft teal brand accents, hand-written display headings, rounded pill controls. |
| [skeumorphism](references/skeumorphism/DESIGN.md) | Real-world mimicry with textured surfaces, 3D effects, and familiar physical metaphors for intuitive digital interfaces. |
| [sleek](references/sleek/DESIGN.md) | Modern minimalist aesthetic with clean lines, intentional color palette, subtle interactions, and consistent spacing. |
| [spacious](references/spacious/DESIGN.md) | Generous whitespace, consistent padding, and grid-based layouts for clean, readable, and breathing interfaces. |
| [square](references/square/DESIGN.md) | Graceful, refined aesthetic with delicate typography, minimal palettes, and polished layouts that exude sophistication. |
| [stitch](references/stitch/DESIGN.md) | Clean, high-contrast enterprise design for data-driven workflows with intuitive drag-and-drop patterns and structured layouts. |
| [storytelling](references/storytelling/DESIGN.md) | Narrative-driven design using visuals, copy, and interaction to guide users through engaging, emotionally resonant journeys. |
| [terracotta](references/terracotta/DESIGN.md) | A sun-baked, clay-toned editorial interface built on warm cream surfaces, ink-brown headlines set in a display serif, and a single terracotta accent. |
| [tetris](references/tetris/DESIGN.md) | Classic block-game inspired design with playful colors, bold display fonts, and compact, high-energy layouts. |
| [vibrant](references/vibrant/DESIGN.md) | Lively, colorful design with bold playful typography, warm accents, and dynamic visual energy. |
| [vintage](references/vintage/DESIGN.md) | 1950s-1990s nostalgia with skeuomorphic touches, grainy textures, retro color palettes, and pixel-style typography. |

## Attribution

These style guides are MIT-licensed. The upstream licence is in `LICENSE`;
each style remains the work of its upstream author. This catalog only indexes them.

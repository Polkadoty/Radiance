# Square Skies 0.7.4 — Weathered Skies

Includes the small group-density adjustment from 0.7.3 (about 20% more connected
groups than the user-approved 0.7.2 CPU sample). Layer heights remain 192/384/640.

Rain now smoothly brings cloud radiance 80% toward 92% of the actual sky radiance,
keeping 20% of lit shape shading. The bright rim term falls to 15% in full rain.
Clear weather is unchanged. This is an art-directed overcast treatment using
the current atmospheric background, with no extra texture samples or rays.
Cloud geometry, opacity and water shaders are unchanged from 0.7.3.

All 46 shaders compile and pass SPIR-V validation. Runtime visual approval is
pending. Prior bundled reports describe their original versions and defaults.

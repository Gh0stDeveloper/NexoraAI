#!/usr/bin/env node
import fs from 'node:fs';
const required=['package.json','Dockerfile','docker-compose.vps.yml','src/app/api/health/route.ts','apps/android/GhostNexoraAndroid/app/build.gradle.kts','.github/workflows/web-api-ci.yml'];
const missing=required.filter(f=>!fs.existsSync(f));
if(missing.length){console.error('Faltan archivos:',missing);process.exit(1)}
console.log('Repositorio validado')

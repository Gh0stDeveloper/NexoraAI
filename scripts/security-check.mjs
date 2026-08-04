#!/usr/bin/env node
import fs from 'node:fs';
const text=fs.readFileSync('.env.vps.example','utf8');
if(/sk-[A-Za-z0-9]/.test(text)){console.error('Secreto real detectado');process.exit(1)}
console.log('Security check básico OK')

import { cpSync, existsSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const source=fileURLToPath(new URL('../dist/fieldnotes/browser/',import.meta.url));
const destination=fileURLToPath(new URL('../../src/main/resources/static/ui/',import.meta.url));
if(!existsSync(new URL('../dist/fieldnotes/browser/index.html',import.meta.url))) throw new Error('Build Angular before staging its assets.');
mkdirSync(destination,{recursive:true});
cpSync(source,destination,{recursive:true});
console.log('Staged Angular assets for Spring Boot.');

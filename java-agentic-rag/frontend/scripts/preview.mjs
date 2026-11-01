// Small static server for browser tests of the production build. API calls are mocked in ui.spec.mjs.
import { createServer } from 'node:http';
import { readFile, access } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, sep, extname } from 'node:path';
const root=fileURLToPath(new URL('../dist/fieldnotes/browser/',import.meta.url));
await access(resolve(root,'index.html'));
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.json':'application/json'};
createServer(async(req,res)=>{
  try {
    const pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
    if(req.method!=='GET' || !pathname.startsWith('/ui/')) { res.writeHead(404); res.end(); return; }
    const file=resolve(root,pathname.slice(4) || 'index.html');
    if(!file.startsWith(resolve(root)+sep)) { res.writeHead(404); res.end(); return; }
    const content=await readFile(file);
    res.writeHead(200,{'Content-Type':types[extname(file)] || 'application/octet-stream'}); res.end(content);
  } catch { res.writeHead(404); res.end(); }
}).listen(4200,'127.0.0.1',()=>console.log('Production-build test preview: http://127.0.0.1:4200/ui/index.html'));

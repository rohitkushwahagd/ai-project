import { ChangeDetectorRef, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ModalDirective } from './modal.directive';
import { ApiService, DocumentSummary, Evidence, Ingest, Result, Source } from './api.service';

interface Message { role:'user'|'assistant'; text:string; status:string; result?:Result; rating:number; comment:string; feedbackState:string; }
@Component({selector:'app-root',standalone:true,imports:[FormsModule,DecimalPipe,ModalDirective],templateUrl:'./app.component.html'})
export class AppComponent {
  private api=inject(ApiService);
  private changeDetector=inject(ChangeDetectorRef);
  view=signal<'chat'|'documents'>('chat'); connected=signal(false); modal=signal<'connection'|'editor'|'source'|null>(null);
  notice=signal(''); error=signal(''); busy=signal(false); saving=signal(false); loading=signal(false);
  messages=signal<Message[]>([]); evidence=signal<Evidence[]>([]); documents=signal<DocumentSummary[]>([]);
  source=signal<Source|null>(null); page=signal(0);
  tokenInput=''; question=''; tools=false; draft:Ingest={title:'',kind:'runbooks',text:''}; editId:string|null=null;
  private controller?:AbortController; private generation=0; private saveKey=''; private savedPayload='';
  private conversation=viewChild<ElementRef<HTMLElement>>('conversation');
  openConnection() { if(this.busy() || this.saving()) { this.notice.set('Finish or stop the current action before changing workspace.'); return; } this.error.set(''); this.modal.set('connection'); }
  resetWorkspace() { this.generation++; this.controller?.abort(); this.messages.set([]); this.evidence.set([]); this.documents.set([]); this.source.set(null); this.page.set(0); this.question=''; this.draft={title:'',kind:'runbooks',text:''}; this.notice.set(''); }
  async connect() {
    if(this.loading()) return;
    this.loading.set(true); this.error.set(''); this.resetWorkspace(); this.connected.set(false); this.api.setToken(this.tokenInput);
    try { const docs=await this.api.list(); this.documents.set(docs); this.connected.set(true); this.tokenInput=''; this.modal.set(null); this.notice.set('Workspace connected. Your token stays in this tab until reload.'); }
    catch(e) { this.api.clear(); this.error.set(this.message(e)); }
    finally { this.loading.set(false); }
  }
  disconnect() { if(this.loading()) return; this.resetWorkspace(); this.api.clear(); this.connected.set(false); this.tokenInput=''; this.modal.set(null); }
  navigate(view:'chat'|'documents') { this.view.set(view); if(view==='documents' && this.connected()) void this.refresh(); }
  async refresh(page=this.page()) {
    if(!this.connected()) { this.openConnection(); return; }
    if(this.loading()) return;
    const generation=this.generation; this.loading.set(true);
    try { const docs=await this.api.list(page); if(generation!==this.generation) return; this.documents.set(docs); this.page.set(page); }
    catch(e) { this.notice.set(this.message(e)); } finally { this.loading.set(false); }
  }
  private update(message:Message, changes:Partial<Message>) {
    Object.assign(message,changes); this.messages.update(items=>[...items]);
    setTimeout(()=>{ const element=this.conversation()?.nativeElement; if(element) element.scrollTop=element.scrollHeight; });
  }
  async ask() {
    if(this.busy() || !this.question.trim()) return;
    if(!this.connected()) { this.openConnection(); return; }
    const question=this.question.trim(); this.question=''; this.evidence.set([]); this.notice.set('');
    const answer:Message={role:'assistant',text:'',status:'Searching your knowledge…',rating:5,comment:'',feedbackState:''};
    this.messages.update(items=>[...items,{role:'user',text:question,status:'',rating:5,comment:'',feedbackState:''},answer]);
    this.busy.set(true); this.controller=new AbortController(); let terminal=false;
    try {
      await this.api.query(question,this.tools,this.controller.signal,(event,data)=>{
        if(event==='sources') { this.evidence.set(JSON.parse(data)); this.update(answer,{status:'Writing an answer from the evidence…'}); }
        if(event==='token') { this.update(answer,{text:answer.text+data}); }
        if(event==='done') { const result:Result=JSON.parse(data); terminal=true; this.evidence.set(result.sources); this.update(answer,{text:result.answer,result,status:`${(result.latencyMs/1000).toFixed(1)} s · ${result.cached?'Cached answer':'Fresh answer'}`}); }
        if(event==='error') { terminal=true; const detail=JSON.parse(data); this.update(answer,{status:detail.message || 'The query failed.'}); }
      });
      if(!terminal) throw new Error('The connection ended before the answer completed. Please retry.');
    } catch(e) { this.update(answer,{status:e instanceof DOMException && e.name==='AbortError'?'Stopped. This answer may be incomplete.':this.message(e)}); }
    finally { this.busy.set(false); this.controller=undefined; }
  }
  stop() { this.controller?.abort(); }
  async rate(message:Message) {
    if(!message.result || message.feedbackState) return;
    this.update(message,{feedbackState:'Saving…'});
    try { await this.api.feedback(message.result.queryId,Number(message.rating),message.comment); this.update(message,{feedbackState:'Feedback saved'}); }
    catch(e) { this.update(message,{feedbackState:''}); this.notice.set(this.message(e)); }
  }
  async showSource(id:string) {
    const generation=this.generation;
    try { const source=await this.api.source(id); if(generation!==this.generation) return; this.source.set(source); this.modal.set('source'); }
    catch(e) { this.notice.set(this.message(e)); }
  }
  newDocument() {
    if(!this.connected()) { this.openConnection(); return; }
    this.editId=null; this.draft={title:'',kind:'runbooks',text:''}; this.saveKey=''; this.error.set(''); this.modal.set('editor');
  }
  async edit(document:DocumentSummary) {
    if(!document.activeSourceId || document.pendingSourceId) return;
    try { const source=await this.api.source(document.activeSourceId); this.editId=source.id; this.draft={title:source.title,kind:source.kind,text:source.content}; this.saveKey=''; this.error.set(''); this.modal.set('editor'); }
    catch(e) { this.notice.set(this.message(e)); }
  }
  async importFile(event:Event) {
    const file=(event.target as HTMLInputElement).files?.[0]; if(!file) return;
    if(!/\.(txt|md)$/i.test(file.name) || file.size>400000) { this.error.set('Choose a .txt or .md file under 400 KB.'); return; }
    const text=await file.text(); if(text.length>100000) { this.error.set('Content must be at most 100,000 characters.'); return; }
    this.draft.text=text; if(!this.draft.title) this.draft.title=file.name.slice(0,120); this.error.set('');
    this.changeDetector.markForCheck();
  }
  async save() {
    if(this.saving()) return;
    const payload=JSON.stringify({id:this.editId,...this.draft});
    if(!this.saveKey || payload!==this.savedPayload) { this.saveKey=crypto.randomUUID(); this.savedPayload=payload; }
    this.saving.set(true); this.error.set('');
    try { await this.api.save({...this.draft},this.editId,this.saveKey); this.modal.set(null); this.view.set('documents'); this.notice.set('Document accepted. Refresh status to check when indexing is complete.'); await this.refresh(0); }
    catch(e) { this.error.set(this.message(e)); } finally { this.saving.set(false); }
  }
  async remove(document:DocumentSummary) {
    if(!confirm(`Delete “${document.title}” and all its versions from retrieval?`)) return;
    try { await this.api.delete(document.id); this.notice.set('Document removed from retrieval. Historical raw text remains in the database.'); await this.refresh(); }
    catch(e) { this.notice.set(this.message(e)); }
  }
  closeModal() { if(this.loading() || this.saving()) return; this.modal.set(null); this.tokenInput=''; }
  private message(error:unknown) { return error instanceof Error?error.message:'Something went wrong. Please retry.'; }
}

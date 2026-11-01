import { Injectable } from '@angular/core';
import { consumeEvents } from './stream.js';
export interface Evidence { chunkId:string; sourceId:string; title:string; text:string; score:number; }
export interface Result { queryId:string; answer:string; sources:Evidence[]; cached:boolean; latencyMs:number; toolTrace:{tool:string;error:boolean;result:string}[]; }
export interface Source { id:string; title:string; kind:string; content:string; status:string; }
export interface DocumentSummary { id:string; title:string; kind:string; status:string; activeSourceId:string|null; pendingSourceId:string|null; }
export interface Ingest { title:string; kind:string; text:string; }
@Injectable({providedIn:'root'})
export class ApiService {
  private token = '';
  setToken(token:string) { this.token = token.trim(); }
  clear() { this.token = ''; }
  private async request(path:string, options:RequestInit = {}) {
    if (!this.token) throw new Error('Connect your workspace first.');
    const response = await fetch(path, {...options, headers: {'Content-Type':'application/json', Authorization:`Bearer ${this.token}`, ...options.headers}});
    if (!response.ok) {
      const hints:Record<number,string> = {401:'Your token is invalid or expired. Connect with a fresh token.',403:'Your token does not grant permission for this action.',404:'This source or query is no longer available.',409:'This document changed or already has a pending replacement. Refresh the library.',429:'The local model is busy. Try again after a running query finishes.'};
      throw new Error(hints[response.status] || `Request failed (${response.status}). Check the service and try again.`);
    }
    return response;
  }
  async list(page=0):Promise<DocumentSummary[]> { return (await this.request(`/api/documents?page=${page}`)).json(); }
  async source(id:string):Promise<Source> { return (await this.request(`/sources/${encodeURIComponent(id)}`)).json(); }
  async save(input:Ingest, id:string|null, key:string):Promise<{id:string;status:string}> {
    return (await this.request(id ? `/sources/${encodeURIComponent(id)}` : '/api/documents', {method:id?'PUT':'POST',headers:{'Idempotency-Key':key},body:JSON.stringify(input)})).json();
  }
  async delete(id:string) { await this.request(`/sources/${encodeURIComponent(id)}`,{method:'DELETE'}); }
  async feedback(queryId:string,rating:number,comment:string) { await this.request('/feedback',{method:'POST',body:JSON.stringify({queryId,rating,comment})}); }
  async query(question:string,tools:boolean,signal:AbortSignal,onEvent:(name:string,data:string)=>void) {
    const response=await this.request('/query',{method:'POST',signal,body:JSON.stringify({question,tools,allowEscalation:false,bypassCache:false})});
    await consumeEvents(response,onEvent);
  }
}

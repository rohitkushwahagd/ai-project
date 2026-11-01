import { AfterViewInit, Directive, ElementRef, inject } from '@angular/core';

// Native modal dialogs provide focus trapping, Escape handling, and focus restoration.
@Directive({selector:'dialog[appModal]',standalone:true})
export class ModalDirective implements AfterViewInit {
  private element=inject(ElementRef<HTMLDialogElement>);
  ngAfterViewInit() { this.element.nativeElement.showModal(); }
}

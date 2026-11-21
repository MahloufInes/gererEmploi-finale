import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MonEmploiComponent } from './mon-emploi.component';

describe('MonEmploiComponent', () => {
  let component: MonEmploiComponent;
  let fixture: ComponentFixture<MonEmploiComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [MonEmploiComponent]
    });
    fixture = TestBed.createComponent(MonEmploiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

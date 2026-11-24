import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PreferencesGroupesComponent } from './preferences-groupes.component';

describe('PreferencesGroupesComponent', () => {
  let component: PreferencesGroupesComponent;
  let fixture: ComponentFixture<PreferencesGroupesComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PreferencesGroupesComponent]
    });
    fixture = TestBed.createComponent(PreferencesGroupesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

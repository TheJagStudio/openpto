import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import type { Observable } from 'rxjs';

import { API_BASE_URL, SKIP_AUTH_REDIRECT, SKIP_ERROR_TOAST } from '../http/http-utils';
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../models';

/** Login/register errors (401 bad credentials, 409 duplicate, 400 validation) are shown in the form. */
const formCall = () => new HttpContext().set(SKIP_ERROR_TOAST, true).set(SKIP_AUTH_REDIRECT, true);

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/api/v1/auth`;

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, body, { context: formCall() });
  }

  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/register`, body, { context: formCall() });
  }

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.base}/me`);
  }
}

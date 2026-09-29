import { FieldError } from '../models/field-error';


export interface AppError {
  readonly code?: string;
  readonly message: string;
  readonly status?: number;
  readonly fieldErrors: readonly FieldError[];
}

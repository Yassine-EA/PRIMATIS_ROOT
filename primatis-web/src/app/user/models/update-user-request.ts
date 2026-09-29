import { RoleCode } from '../../auth/models/role';


export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string | null;
  roles?: RoleCode[];
  registrationDate?: string;
  memberExpirationDate?: string | null;
  blockedReason?: string | null;
}

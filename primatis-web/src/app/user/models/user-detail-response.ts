import { RoleCode } from '../../auth/models/role';
import { UserResponse } from './user-response';


export interface UserDetailResponse {
  user: UserResponse;
  roles: RoleCode[];
}

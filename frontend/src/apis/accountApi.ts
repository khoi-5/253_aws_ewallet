import axiosClient from './axiosClient'
import type { AuthUser, Wallet } from './authApi'

export type AccountRole = 'user' | 'admin'
export type AccountStatus = 'active' | 'blocked'

export type UserProfile = {
  fullName: string | null
  dateOfBirth: string | null
  address: string | null
}

export type AdminProfile = {
  fullName: string | null
  position: string | null
}

type BaseAccount = {
  id: number
  phone: string
  email: string | null
  emailVerified: boolean
  role: AccountRole
  status: AccountStatus
  fullName: string | null
  position?: string | null
}

export type UserAccountResponse = BaseAccount & {
  role: 'user'
  profile: UserProfile
  wallet: Wallet | null
}

export type AdminAccountResponse = BaseAccount & {
  role: 'admin'
  profile: AdminProfile
  wallet: null
  position: string | null
}

export type CurrentAccountResponse = UserAccountResponse | AdminAccountResponse

export type UpdateUserProfileRequest = {
  fullName: string
  dateOfBirth?: string | null
  address?: string | null
}

export type UpdateAdminProfileRequest = {
  fullName: string
}

export type UpdateProfileRequest =
  | UpdateUserProfileRequest
  | UpdateAdminProfileRequest

const getAuthHeaders = () => {
  const token = localStorage.getItem('token')

  return {
    Authorization: `Bearer ${token}`,
  }
}

export function accountToAuthUser(account: CurrentAccountResponse): AuthUser {
  return {
    id: account.id,
    phone: account.phone,
    email: account.email,
    emailVerified: account.emailVerified,
    role: account.role,
    status: account.status,
    fullName: account.profile.fullName,
    position:
      account.role === 'admin' ? account.profile.position : account.position,
  }
}

export const accountApi = {
  getCurrentAccount: async () => {
    const response = await axiosClient.get<CurrentAccountResponse>(
      '/account/me',
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },

  updateCurrentAccountProfile: async (data: UpdateProfileRequest) => {
    const response = await axiosClient.patch<CurrentAccountResponse>(
      '/account/me',
      data,
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },
}

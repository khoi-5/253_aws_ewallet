import axiosClient from './axiosClient'

export type AdminAccount = {
  id: number
  phone: string
  fullName: string | null
  role: 'admin'
  status: 'active' | 'blocked'
  position: string | null
}

export type AdminSummary = {
  totalUsers: number
  activeUsers: number
  blockedUsers: number
  totalTransactions: number
}

export type AdminDashboardResponse = {
  admin: AdminAccount
  summary: AdminSummary
}

export type AdminUserStatus = 'active' | 'blocked'

export type AdminUser = {
  id: number
  phone: string
  fullName: string | null
  role: 'user'
  status: AdminUserStatus
  walletId: number | null
  balance: number | null
  createdAt: string | null
  updatedAt: string | null
}

export type AdminUsersResponse = {
  users: AdminUser[]
}

export type UpdateUserStatusResponse = {
  message: string
  user: {
    id: number
    phone: string
    fullName: string | null
    status: AdminUserStatus
  }
}

export type AdminTransactionType = 'deposit' | 'transfer' | 'payment'
export type AdminTransactionStatus = 'success' | 'failed'

export type AdminTransaction = {
  id: number
  transactionCode: string
  type: AdminTransactionType
  status: AdminTransactionStatus
  amount: number
  description: string | null
  createdAt: string | null
  senderWalletId: number | null
  senderUserId: number | null
  senderPhone: string | null
  senderName: string | null
  receiverWalletId: number | null
  receiverUserId: number | null
  receiverPhone: string | null
  receiverName: string | null
  serviceId: number | null
  serviceName: string | null
  createdByUserId: number | null
  createdByPhone: string | null
  createdByName: string | null
  balanceBefore: number | null
  balanceAfter: number | null
}

export type AdminTransactionFilters = {
  page?: number
  size?: 10 | 20 | 50
  type?: 'all' | AdminTransactionType
  status?: 'all' | AdminTransactionStatus
  search?: string
  phone?: string
  dateFrom?: string
  dateTo?: string
  sortDirection?: 'asc' | 'desc'
}

export type AdminTransactionPagination = {
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}

export type AdminTransactionsResponse = {
  transactions: AdminTransaction[]
  pagination: AdminTransactionPagination
}

export type AdminService = {
  id: number
  name: string
  price: number
  description: string | null
  isActive: boolean
  createdAt: string | null
  updatedAt: string | null
}
export type CreateServiceRequest = { name: string; price: number; description: string; isActive: boolean }
export type UpdateServiceRequest = Partial<Pick<CreateServiceRequest, 'name' | 'price' | 'description'>>
export type UpdateServiceStatusRequest = { isActive: boolean }
export type AdminServicesResponse = { services: AdminService[] }
export type AdminServiceMutationResponse = { message: string; service: AdminService }

const getAuthHeaders = () => {
  const token = localStorage.getItem('token')

  return {
    Authorization: `Bearer ${token}`,
  }
}

export const adminApi = {
  getAdminDashboard: async () => {
    const response = await axiosClient.get<AdminDashboardResponse>(
      '/admin/dashboard',
      { headers: getAuthHeaders() },
    )
    return response.data
  },

  getUsers: async () => {
    const response = await axiosClient.get<AdminUsersResponse>('/admin/users', {
      headers: getAuthHeaders(),
    })
    return response.data
  },

  getTransactions: async (
    filters: AdminTransactionFilters,
    signal?: AbortSignal,
  ) => {
    const response = await axiosClient.get<AdminTransactionsResponse>(
      '/admin/transactions',
      { headers: getAuthHeaders(), params: filters, signal },
    )
    return response.data
  },

  getServices: async () => {
    const response = await axiosClient.get<AdminServicesResponse>('/admin/services', { headers: getAuthHeaders() })
    return response.data
  },
  createService: async (payload: CreateServiceRequest) => {
    const response = await axiosClient.post<AdminServiceMutationResponse>('/admin/services', payload, { headers: getAuthHeaders() })
    return response.data
  },
  updateService: async (serviceId: number, payload: UpdateServiceRequest) => {
    const response = await axiosClient.patch<AdminServiceMutationResponse>(`/admin/services/${serviceId}`, payload, { headers: getAuthHeaders() })
    return response.data
  },
  updateServiceStatus: async (serviceId: number, isActive: boolean) => {
    const payload: UpdateServiceStatusRequest = { isActive }
    const response = await axiosClient.patch<AdminServiceMutationResponse>(`/admin/services/${serviceId}/status`, payload, { headers: getAuthHeaders() })
    return response.data
  },

  updateUserStatus: async (userId: number, status: AdminUserStatus) => {
    const response = await axiosClient.patch<UpdateUserStatusResponse>(
      `/admin/users/${userId}/status`,
      { status },
      { headers: getAuthHeaders() },
    )
    return response.data
  },
}

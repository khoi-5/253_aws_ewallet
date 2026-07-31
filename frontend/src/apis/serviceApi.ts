import axiosClient from './axiosClient'
import type { WalletTransaction } from './walletApi'

export type Service = {
  id: number
  name: string
  price: number
  description: string | null
  isActive: boolean
}

export type ServicesResponse = {
  services: Service[]
}

export type PayServiceRequest = {
  serviceId: number
  description?: string
}

export type PayServiceResponse = {
  message: string
  balance: number
  transaction: WalletTransaction
}

const getAuthHeaders = () => {
  const token = localStorage.getItem('token')

  return {
    Authorization: `Bearer ${token}`,
  }
}

export const serviceApi = {
  getActiveServices: async () => {
    const response = await axiosClient.get<ServicesResponse>(
      '/user/wallet/services',
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },

  payService: async (serviceId: number, description?: string) => {
    const payload: PayServiceRequest = {
      serviceId,
      description,
    }

    const response = await axiosClient.post<PayServiceResponse>(
      '/user/wallet/payments',
      payload,
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },
}

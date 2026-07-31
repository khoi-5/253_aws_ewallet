import axiosClient from './axiosClient'
import type { AuthUser, Wallet } from './authApi'

export type WalletInfoResponse = {
  user: AuthUser
  wallet: Wallet
}

export type TransferPayload = {
  receiverPhone: string
  amount: number
  description?: string
}

export type TransferResponse = {
  message: string
  balance: number
  transaction: {
    transactionCode: string
    amount: number
    receiverPhone: string
  }
}

export type RecipientPreview = {
  phone: string
  fullName: string
}

export type DepositPayload = {
  amount: number
  description?: string
}

export type WalletTransactionType = 'deposit' | 'transfer' | 'payment' | string

export type WalletTransactionStatus = 'success' | 'failed' | string

export type WalletTransaction = {
  id: number
  transactionCode: string
  type: WalletTransactionType
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
  amount: number
  balanceBefore: number | null
  balanceAfter: number | null
  status: WalletTransactionStatus
  description: string | null
  createdAt: string | null
}

export type TransactionsResponse = {
  transactions: WalletTransaction[]
}

export type DepositResponse = {
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

export const walletApi = {
  getMyWallet: async () => {
    const response = await axiosClient.get<WalletInfoResponse>(
      '/user/wallet/me',
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },

  transferMoney: async (data: TransferPayload) => {
    const response = await axiosClient.post<TransferResponse>(
      '/user/wallet/transfer',
      data,
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },

  getRecipient: async (phone: string, signal?: AbortSignal) => {
    const response = await axiosClient.get<RecipientPreview>(
      '/user/wallet/recipient',
      {
        headers: getAuthHeaders(),
        params: { phone },
        signal,
      },
    )
    return response.data
  },

  depositMoney: async (data: DepositPayload) => {
    const response = await axiosClient.post<DepositResponse>(
      '/user/wallet/deposit',
      data,
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },

  getMyTransactions: async () => {
    const response = await axiosClient.get<TransactionsResponse>(
      '/user/wallet/transactions',
      {
        headers: getAuthHeaders(),
      },
    )
    return response.data
  },
}

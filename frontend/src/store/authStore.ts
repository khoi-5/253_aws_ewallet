import { create } from 'zustand'
import type { AuthUser, Wallet } from '../apis/authApi'

type AuthState = {
  token: string | null
  user: AuthUser | null
  wallet: Wallet | null
  setAuth: (token: string, user: AuthUser, wallet?: Wallet) => void
  setAccount: (user: AuthUser) => void
  setWalletData: (user: AuthUser, wallet: Wallet) => void
  logout: () => void
}

const savedToken = localStorage.getItem('token')
const savedUser = localStorage.getItem('user')
const savedWallet = localStorage.getItem('wallet')

const parseSavedJson = <T,>(value: string | null, key: string) => {
  if (!value) {
    return null
  }

  try {
    return JSON.parse(value) as T
  } catch {
    localStorage.removeItem(key)
    return null
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  token: savedToken,
  user: parseSavedJson<AuthUser>(savedUser, 'user'),
  wallet: parseSavedJson<Wallet>(savedWallet, 'wallet'),

  setAuth: (token, user, wallet) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    if (wallet) {
      localStorage.setItem('wallet', JSON.stringify(wallet))
    } else {
      localStorage.removeItem('wallet')
    }
    set({ token, user, wallet: wallet || null })
  },

  setAccount: (user) => {
    localStorage.setItem('user', JSON.stringify(user))
    set({ user })
  },

  setWalletData: (user, wallet) => {
    localStorage.setItem('user', JSON.stringify(user))
    localStorage.setItem('wallet', JSON.stringify(wallet))
    set({ user, wallet })
  },

  logout: () => {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('wallet')
    set({ token: null, user: null, wallet: null })
  },
}))

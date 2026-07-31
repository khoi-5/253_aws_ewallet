import { z } from 'zod'

const amountPattern = /^\d+(\.\d{1,2})?$/

export const depositSchema = z.object({
  amount: z
    .string()
    .trim()
    .min(1, 'Amount is required')
    .refine((value) => Number.isFinite(Number(value)), {
      message: 'Amount must be numeric',
    })
    .refine((value) => amountPattern.test(value), {
      message: 'Amount can have at most 2 decimal places',
    })
    .refine((value) => Number(value) >= 1, {
      message: 'Amount must be at least 1.00',
    })
    .refine((value) => Number(value) <= 10000000, {
      message: 'Amount must be at most 10,000,000.00',
    }),
  description: z
    .string()
    .trim()
    .max(255, 'Description must be at most 255 characters'),
})

export type DepositForm = z.infer<typeof depositSchema>

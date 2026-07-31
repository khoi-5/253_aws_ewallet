import { z } from 'zod'

const pricePattern = /^\d+(\.\d{1,2})?$/

export const serviceFormSchema = z.object({
  name: z.string().trim().min(2, 'Name must be at least 2 characters').max(100, 'Name must be at most 100 characters'),
  price: z.string().trim().min(1, 'Price is required')
    .refine((value) => Number.isFinite(Number(value)), 'Price must be numeric')
    .refine((value) => pricePattern.test(value), 'Price can have at most 2 decimal places')
    .refine((value) => Number(value) > 0, 'Price must be greater than 0')
    .refine((value) => Number(value) <= 10000000, 'Price must be at most 10,000,000'),
  description: z.string().trim().max(255, 'Description must be at most 255 characters'),
  isActive: z.boolean(),
})

export type ServiceForm = z.infer<typeof serviceFormSchema>

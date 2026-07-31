import { z } from 'zod'

const todayText = new Date().toISOString().slice(0, 10)

const fullNameRule = z
  .string()
  .trim()
  .min(2, 'Full name must be at least 2 characters')
  .max(100, 'Full name must be at most 100 characters')

const optionalDateRule = z
  .string()
  .trim()
  .refine((value) => !value || /^\d{4}-\d{2}-\d{2}$/.test(value), {
    message: 'Date of birth must be a valid date',
  })
  .refine((value) => !value || value <= todayText, {
    message: 'Date of birth must not be in the future',
  })

export const userProfileSchema = z.object({
  fullName: fullNameRule,
  dateOfBirth: optionalDateRule,
  address: z
    .string()
    .trim()
    .max(255, 'Address must be at most 255 characters'),
})

export const adminProfileSchema = z.object({
  fullName: fullNameRule,
})

export type UserProfileForm = z.infer<typeof userProfileSchema>
export type AdminProfileForm = z.infer<typeof adminProfileSchema>

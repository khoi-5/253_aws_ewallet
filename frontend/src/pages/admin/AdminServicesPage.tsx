import axios from 'axios'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { adminApi, type AdminService } from '../../apis/adminApi'
import ConfirmationModal from '../../components/ConfirmationModal'
import { useToast } from '../../hooks/useToast'
import { serviceFormSchema, type ServiceForm } from '../../schema/serviceSchema'

type StatusFilter = 'all' | 'active' | 'inactive'
type FormErrors = Partial<Record<keyof ServiceForm, string>>
const priceFormatter = new Intl.NumberFormat('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dateFormatter = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
const emptyForm: ServiceForm = { name: '', price: '', description: '', isActive: true }
const formatPrice = (value: number) => `${priceFormatter.format(Number(value))} USD`
const formatDate = (value: string | null) => {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date)
}

function ServiceFormModal({ service, saving, onSave, onClose }: {
  service: AdminService | null; saving: boolean; onSave: (form: ServiceForm) => Promise<void>; onClose: () => void
}) {
  const initial: ServiceForm = service
    ? { name: service.name, price: String(service.price), description: service.description || '', isActive: service.isActive }
    : emptyForm
  const [form, setForm] = useState<ServiceForm>(initial)
  const [errors, setErrors] = useState<FormErrors>({})
  const unchanged = service !== null && form.name.trim() === initial.name && form.price === initial.price
    && form.description.trim() === initial.description
  useEffect(() => {
    const close = (event: KeyboardEvent) => event.key === 'Escape' && !saving && onClose()
    window.addEventListener('keydown', close); return () => window.removeEventListener('keydown', close)
  }, [onClose, saving])
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const result = serviceFormSchema.safeParse(form)
    if (!result.success) {
      const fields = result.error.flatten().fieldErrors
      setErrors({ name: fields.name?.[0], price: fields.price?.[0], description: fields.description?.[0] }); return
    }
    setErrors({}); await onSave(result.data)
  }
  return <div className="modal-backdrop" onMouseDown={() => !saving && onClose()}>
    <form className="confirmation-modal service-form-modal" onSubmit={submit} onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
      <div><span className="eyebrow">Service management</span><h2>{service ? 'Edit service' : 'Add service'}</h2><p>{service ? 'Update service information.' : 'Create a service for wallet users.'}</p></div>
      <div className="service-form-fields">
        <label>Name<input autoFocus maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />{errors.name && <span className="field-error">{errors.name}</span>}</label>
        <label>Price<input type="number" min="0.01" max="10000000" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />{errors.price && <span className="field-error">{errors.price}</span>}</label>
        <label>Description<textarea rows={4} maxLength={255} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />{errors.description && <span className="field-error">{errors.description}</span>}</label>
        {!service && <label className="service-checkbox"><input type="checkbox" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} /> Active immediately</label>}
      </div>
      <div className="confirmation-modal-actions"><button className="primary-button" disabled={saving || unchanged}>{saving ? 'Saving...' : service ? 'Save changes' : 'Create service'}</button><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>Cancel</button></div>
    </form>
  </div>
}

export default function AdminServicesPage() {
  const { showToast } = useToast()
  const [services, setServices] = useState<AdminService[]>([])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<StatusFilter>('all')
  const [editing, setEditing] = useState<AdminService | null | undefined>(undefined)
  const [pendingStatus, setPendingStatus] = useState<AdminService | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const data = await adminApi.getServices(); setServices(data.services) }
    catch (cause) { const message = axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message || cause.message : 'Cannot load services'; setError(message); showToast(message, 'error') }
    finally { setLoading(false) }
  }, [showToast])
  useEffect(() => { void Promise.resolve().then(load) }, [load])
  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return services.filter((service) => (status === 'all' || (status === 'active') === service.isActive)
      && (!term || service.name.toLowerCase().includes(term) || (service.description || '').toLowerCase().includes(term)))
  }, [search, services, status])
  const replace = (service: AdminService) => setServices((items) => items.map((item) => item.id === service.id ? service : item))
  const save = async (form: ServiceForm) => {
    if (saving) return
    setSaving(true)
    try {
      if (editing) {
        const data = await adminApi.updateService(editing.id, { name: form.name.trim(), price: Number(form.price), description: form.description.trim() })
        replace(data.service); showToast('Service updated successfully.', 'success')
      } else {
        const data = await adminApi.createService({ name: form.name.trim(), price: Number(form.price), description: form.description.trim(), isActive: form.isActive })
        setServices((items) => [data.service, ...items]); showToast('Service created successfully.', 'success')
      }
      setEditing(undefined)
    } catch (cause) { showToast(axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message || cause.message : 'Unable to save service.', 'error') }
    finally { setSaving(false) }
  }
  const changeStatus = async () => {
    if (!pendingStatus || saving) return
    setSaving(true)
    try { const data = await adminApi.updateServiceStatus(pendingStatus.id, !pendingStatus.isActive); replace(data.service); showToast(data.service.isActive ? 'Service activated successfully.' : 'Service deactivated successfully.', 'success'); setPendingStatus(null) }
    catch (cause) { showToast(axios.isAxiosError<{ message?: string }>(cause) ? cause.response?.data?.message || cause.message : 'Unable to update service status.', 'error') }
    finally { setSaving(false) }
  }
  const actions = (service: AdminService) => <div className="service-admin-actions"><button className="secondary-button" onClick={() => setEditing(service)}>Edit</button><button className={service.isActive ? 'danger-button' : 'primary-button'} onClick={() => setPendingStatus(service)}>{service.isActive ? 'Deactivate' : 'Activate'}</button></div>
  return <main className="dashboard-page admin-page">
    {editing !== undefined && <ServiceFormModal service={editing} saving={saving} onSave={save} onClose={() => setEditing(undefined)} />}
    {pendingStatus && <ConfirmationModal title={pendingStatus.isActive ? 'Confirm deactivation' : 'Confirm activation'} message={pendingStatus.isActive ? `Deactivate "${pendingStatus.name}"? Users will no longer be able to pay for this service.` : `Activate "${pendingStatus.name}"? Users will be able to use this service again.`} confirmLabel={pendingStatus.isActive ? 'Deactivate' : 'Activate'} confirmButtonClassName={pendingStatus.isActive ? 'danger-button' : 'primary-button'} isConfirming={saving} onConfirm={changeStatus} onCancel={() => setPendingStatus(null)} />}
    <section className="dashboard-hero"><div><span className="eyebrow">Service Management</span><h1>Wallet services</h1><p>Create, update, activate, and deactivate payment services.</p></div><button className="primary-button" onClick={() => setEditing(null)}>Add service</button></section>
    <section className="dashboard-card">
      <div className="admin-user-toolbar"><label>Search<input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Name or description" /></label><label>Status<select value={status} onChange={(e) => setStatus(e.target.value as StatusFilter)}><option value="all">All</option><option value="active">Active</option><option value="inactive">Inactive</option></select></label></div>
      {error && <div className="transaction-state"><p>{error}</p><button className="secondary-button" onClick={() => void load()}>Retry</button></div>}
      {loading && !services.length && <div className="transaction-state">Loading services...</div>}
      {!loading && !error && !filtered.length && <div className="transaction-state">No services match the filters.</div>}
      {filtered.length > 0 && <><div className="admin-service-table-wrap"><table className="admin-service-table"><thead><tr><th>ID</th><th>Name</th><th>Price</th><th>Description</th><th>Status</th><th>Created</th><th>Updated</th><th>Actions</th></tr></thead><tbody>{filtered.map((service) => <tr key={service.id}><td>{service.id}</td><td><strong>{service.name}</strong></td><td>{formatPrice(service.price)}</td><td>{service.description || '—'}</td><td><span className={`admin-status-badge ${service.isActive ? 'active' : 'blocked'}`}>{service.isActive ? 'Active' : 'Inactive'}</span></td><td>{formatDate(service.createdAt)}</td><td>{formatDate(service.updatedAt)}</td><td>{actions(service)}</td></tr>)}</tbody></table></div><div className="admin-service-card-list">{filtered.map((service) => <article className="admin-user-card" key={service.id}><div className="admin-user-card-top"><div><strong>{service.name}</strong><span>{formatPrice(service.price)}</span></div><span className={`admin-status-badge ${service.isActive ? 'active' : 'blocked'}`}>{service.isActive ? 'Active' : 'Inactive'}</span></div><p>{service.description || 'No description'}</p><div className="transaction-meta-grid"><div><span>Created</span><strong>{formatDate(service.createdAt)}</strong></div><div><span>Updated</span><strong>{formatDate(service.updatedAt)}</strong></div></div>{actions(service)}</article>)}</div></>}
    </section>
  </main>
}

import { useEffect, useId, useRef } from 'react'

type ConfirmationModalProps = {
  title: string
  message: string
  confirmLabel: string
  isConfirming: boolean
  isConfirmDisabled?: boolean
  confirmButtonClassName?: string
  onConfirm: () => void
  onCancel: () => void
}

function ConfirmationModal({
  title,
  message,
  confirmLabel,
  isConfirming,
  isConfirmDisabled = false,
  confirmButtonClassName = 'primary-button',
  onConfirm,
  onCancel,
}: ConfirmationModalProps) {
  const titleId = useId()
  const descriptionId = useId()
  const cancelButtonRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    cancelButtonRef.current?.focus()
  }, [])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isConfirming) {
        onCancel()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isConfirming, onCancel])

  return (
    <div
      className="modal-backdrop"
      onMouseDown={() => {
        if (!isConfirming) {
          onCancel()
        }
      }}
    >
      <div
        className="confirmation-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div>
          <span className="eyebrow">Confirm action</span>
          <h2 id={titleId}>{title}</h2>
          <p id={descriptionId}>{message}</p>
        </div>
        <div className="confirmation-modal-actions">
          <button
            type="button"
            className={confirmButtonClassName}
            onClick={onConfirm}
            disabled={isConfirming || isConfirmDisabled}
          >
            {isConfirming ? 'Updating...' : confirmLabel}
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={onCancel}
            disabled={isConfirming}
            ref={cancelButtonRef}
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmationModal

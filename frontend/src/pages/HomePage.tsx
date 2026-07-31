import { Link } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'

function HomePage() {
  const token = useAuthStore((state) => state.token)

  return (
    <main className="home-page">
      <section className="hero-section split-layout">
        <div className="hero-copy layout-left" style={{ position: 'relative', zIndex: 10 }}>
          {/* Decorative sphere */}
          <div style={{ position: 'absolute', top: '-60px', left: '160px', width: '80px', height: '80px', borderRadius: '50%', background: 'radial-gradient(circle at 30% 30%, #fff, #e879f9 60%, #a855f7)', opacity: 0.8, filter: 'blur(1px)', zIndex: -1 }}></div>
          {/* Decorative dots */}
          <div style={{ position: 'absolute', top: '-100px', left: '-80px', width: '120px', height: '120px', backgroundImage: 'radial-gradient(#cbd5e1 2px, transparent 2px)', backgroundSize: '24px 24px', zIndex: -1 }}></div>

          <span className="eyebrow text-blue" style={{ fontSize: '14px', letterSpacing: '0.1em' }}>CLOUD WALLET</span>
          <h1 style={{ fontSize: '56px', lineHeight: 1.1, marginTop: '24px', marginBottom: '16px' }}>
            A simple cloud wallet<br />for <span className="highlight-text">digital payments.</span>
          </h1>
          <p style={{ color: '#475569', fontSize: '18px', lineHeight: 1.6, maxWidth: '500px' }}>
            Register with your phone number, receive an initial balance,
            transfer money, pay services, and track transactions.
          </p>
          {!token && (
            <div className="hero-actions" style={{display: 'flex', gap: '16px', marginTop: '32px'}}>
              <Link className="primary-button gradient-button" to="/register" style={{padding: '16px 32px', fontSize: '16px', borderRadius: '12px'}}>
                Get Started
              </Link>
              <Link className="secondary-button" to="/login" style={{padding: '16px 32px', fontSize: '16px', borderRadius: '12px', border: '1px solid #c084fc', color: '#0f172a', fontWeight: 'bold', background: '#fff', transition: 'border-color 0.2s'}}>
                Log in
              </Link>
            </div>
          )}
        </div>

        <div className="layout-right illustration-container" style={{background: 'rgba(238, 242, 255, 0.6)', borderRadius: '24px', padding: '40px', border: '1px solid rgba(255, 255, 255, 0.5)'}}>
          <img src="/images/services-illustration.png" alt="Cloud Wallet Features" style={{mixBlendMode: 'normal'}} />
        </div>
      </section>

      <section className="feature-grid">
        <article>
          <h2>One Account One Wallet</h2>
          <p>Create one account and receive one personal wallet.</p>
        </article>
        <article>
          <h2>Mock Deposit</h2>
          <p>Add USD funds to your wallet balance.</p>
        </article>
        <article>
          <h2>Fast Transfer</h2>
          <p>Send USD to another account by phone number.</p>
        </article>
        <article>
          <h2>Virtual Payment</h2>
          <p>Pay available services with your wallet balance.</p>
        </article>
        <article>
          <h2>Transaction History</h2>
          <p>Track deposits, transfers, payments, and account activity.</p>
        </article>
      </section>
    </main>
  )
}

export default HomePage

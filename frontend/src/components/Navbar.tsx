import { Link, useLocation } from 'react-router-dom'
import { Compass, Clock, BarChart3 } from 'lucide-react'

export default function Navbar() {
  const location = useLocation()

  const links = [
    { to: '/', label: '规划', icon: Compass },
    { to: '/history', label: '历史', icon: Clock },
    { to: '/stats', label: '统计', icon: BarChart3 },
  ]

  return (
    <header className="sticky top-0 z-50 bg-sand-50/80 backdrop-blur-lg border-b border-sand-200/50">
      <div className="max-w-5xl mx-auto px-4 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2.5 group">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-sand-400 to-coral-400 flex items-center justify-center shadow-sm group-hover:shadow-md transition-shadow">
            <Compass size={18} className="text-white" />
          </div>
          <span className="font-display font-semibold text-lg text-gray-800">
            ATrip<span className="text-sand-600">Plan</span>
          </span>
        </Link>

        <nav className="flex items-center gap-1">
          {links.map(link => {
            const active = location.pathname === link.to
            const Icon = link.icon
            return (
              <Link
                key={link.to}
                to={link.to}
                className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all duration-200 ${
                  active
                    ? 'bg-sand-200/60 text-sand-800 shadow-sm'
                    : 'text-gray-500 hover:text-gray-700 hover:bg-sand-100/60'
                }`}
              >
                <Icon size={16} />
                {link.label}
              </Link>
            )
          })}
        </nav>
      </div>
    </header>
  )
}

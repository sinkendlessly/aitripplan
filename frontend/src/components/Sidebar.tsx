import { Link, useLocation } from 'react-router-dom'
import { Compass, Clock, BarChart3, Menu, X } from 'lucide-react'
import { useState } from 'react'

const links = [
  { to: '/', label: '规划', icon: Compass },
  { to: '/history', label: '历史', icon: Clock },
  { to: '/stats', label: '统计', icon: BarChart3 },
]

export default function Sidebar() {
  const location = useLocation()
  const [open, setOpen] = useState(false)

  const nav = (
    <nav className="flex flex-col gap-1 flex-1">
      {links.map(link => {
        const active = link.to === '/'
          ? location.pathname === '/'
          : location.pathname.startsWith(link.to)
        const Icon = link.icon
        return (
          <Link
            key={link.to}
            to={link.to}
            onClick={() => setOpen(false)}
            className={`flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
              active
                ? 'bg-sand-200/70 text-sand-800 shadow-sm'
                : 'text-gray-500 hover:text-gray-700 hover:bg-sand-100/50'
            }`}
          >
            <Icon size={18} />
            {link.label}
          </Link>
        )
      })}
    </nav>
  )

  return (
    <>
      {/* Mobile hamburger */}
      <button
        onClick={() => setOpen(!open)}
        className="fixed top-4 left-4 z-50 lg:hidden w-10 h-10 rounded-xl bg-white/90 backdrop-blur border border-sand-200/50 shadow-sm flex items-center justify-center text-gray-500"
      >
        {open ? <X size={18} /> : <Menu size={18} />}
      </button>

      {/* Mobile overlay + sidebar */}
      {open && (
        <div className="fixed inset-0 z-40 lg:hidden" onClick={() => setOpen(false)}>
          <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" />
        </div>
      )}

      <aside
        className={`
          fixed top-0 left-0 z-40 h-full w-60 bg-sand-50/90 backdrop-blur-xl
          border-r border-sand-200/50 flex flex-col
          transition-transform duration-300
          ${open ? 'translate-x-0' : '-translate-x-full'}
          lg:translate-x-0 lg:static lg:z-auto
        `}
      >
        {/* Logo */}
        <div className="p-5 border-b border-sand-200/30">
          <Link to="/" onClick={() => setOpen(false)} className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-sand-400 to-coral-400 flex items-center justify-center shadow-sm group-hover:shadow-md transition-shadow flex-shrink-0">
              <Compass size={18} className="text-white" />
            </div>
            <span className="font-display font-semibold text-lg text-gray-800">
              ATrip<span className="text-sand-600">Plan</span>
            </span>
          </Link>
        </div>

        {/* Navigation */}
        <div className="flex-1 p-3 flex flex-col">
          {nav}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-sand-200/30">
          <p className="text-xs text-gray-300 text-center">AI 旅行规划</p>
        </div>
      </aside>
    </>
  )
}

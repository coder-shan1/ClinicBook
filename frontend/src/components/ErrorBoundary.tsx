import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * Catches render-time errors so a single broken screen never blanks the app.
 * The fallback keeps the header/navigation usable and offers a retry.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep the details in the console for developers; users get the friendly UI.
    console.error('Unhandled UI error', error, info.componentStack)
  }

  private reset = () => {
    this.setState({ hasError: false })
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div
        role="alert"
        aria-atomic="true"
        className="mx-auto max-w-xl space-y-4 rounded-lg border border-red-200 bg-red-50 p-6 text-red-900"
      >
        <h1 className="text-xl font-bold">Something went wrong</h1>
        <p className="text-sm leading-relaxed">
          This screen hit an unexpected error. Your data is safe — try again, or go back to a known
          page.
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={this.reset}
            className="rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-800"
          >
            Try again
          </button>
          <Link
            to="/"
            onClick={this.reset}
            className="rounded-md border border-red-300 bg-white px-4 py-2 text-sm font-semibold text-red-800 hover:bg-red-100"
          >
            Go home
          </Link>
        </div>
      </div>
    )
  }
}

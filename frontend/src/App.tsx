import { Navigate, Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth.tsx'
import { AppLayout } from './components/AppLayout.tsx'
import { AuthPage } from './pages/AuthPage.tsx'
import { ChatPage } from './pages/ChatPage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'
import { WorkspacePage } from './pages/WorkspacePage.tsx'
import { WorkspacesPage } from './pages/WorkspacesPage.tsx'

export default function App() {
  return (
    <Routes>
      {/* The keys reset the form when you switch between the two pages. */}
      <Route path="/login" element={<AuthPage key="login" mode="login" />} />
      <Route path="/register" element={<AuthPage key="register" mode="register" />} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route index element={<Navigate to="/workspaces" replace />} />
        <Route path="workspaces" element={<WorkspacesPage />} />
        <Route path="workspaces/:workspaceId" element={<WorkspacePage />} />
        <Route path="workspaces/:workspaceId/chat" element={<ChatPage />} />
        <Route path="workspaces/:workspaceId/chat/:sessionId" element={<ChatPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

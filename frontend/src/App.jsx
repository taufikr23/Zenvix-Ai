import { SessionProvider, useSession } from './lib/session';
import { AuthPage } from './components/AuthPage';
import { Workspace } from './components/Workspace';
import { Brand, Spinner } from './components/UI';
function Content() {
  const { session, checking } = useSession();
  if (checking) return <main className="boot"><Brand/><Spinner label="Menyiapkan ruang kerja…"/></main>;
  return session ? <Workspace key={session.user.id}/> : <AuthPage/>;
}
export function App() { return <SessionProvider><Content/></SessionProvider>; }

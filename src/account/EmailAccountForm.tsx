import React, {useRef, useState} from 'react';
import {StyleSheet, Text, TextInput, TouchableOpacity, View} from 'react-native';
import {useUser} from './UserContext';
import {theme} from '../theme';

export function EmailAccountForm() {
  const account = useUser();
  const [creating, setCreating] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);
  const submit = async () => {
    if (busy.current) return;
    busy.current = true; setPending(true); setError(null); setMessage(null);
    try {
      if (creating && password !== confirmation) throw new Error('Passwords do not match.');
      if (creating) {
        const result = await account.signUpWithPassword(email, password);
        if (result === 'confirmation_required') {
          setMessage('Check your email to confirm your account, then sign in here.');
          setCreating(false);
        }
      } else await account.signInWithPassword(email, password);
      setPassword(''); setConfirmation('');
    } catch (cause) {
      console.error('Email account operation failed.', cause);
      setError(cause instanceof Error ? cause.message : 'Sign-in failed. Please try again.');
    } finally {busy.current = false; setPending(false);}
  };
  return <View style={s.form}>
    <Text style={s.heading}>{creating ? 'Create an account' : 'Sign in with email'}</Text>
    <TextInput accessibilityLabel="Email address" placeholder="Email address" placeholderTextColor={theme.textDim} style={s.input} value={email} onChangeText={setEmail} autoCapitalize="none" autoCorrect={false} keyboardType="email-address" autoComplete="email" editable={!pending} />
    <TextInput accessibilityLabel={creating ? 'New password' : 'Password'} placeholder={creating ? 'Password (8+ characters)' : 'Password'} placeholderTextColor={theme.textDim} style={s.input} value={password} onChangeText={setPassword} secureTextEntry autoCapitalize="none" autoCorrect={false} autoComplete={creating ? 'new-password' : 'current-password'} editable={!pending} />
    {creating && <TextInput accessibilityLabel="Confirm password" placeholder="Confirm password" placeholderTextColor={theme.textDim} style={s.input} value={confirmation} onChangeText={setConfirmation} secureTextEntry autoCapitalize="none" autoCorrect={false} autoComplete="new-password" editable={!pending} />}
    {error && <Text accessibilityRole="alert" style={{color: theme.danger}}>{error}</Text>}
    {message && <Text accessibilityLiveRegion="polite" style={{color: theme.accent}}>{message}</Text>}
    <TouchableOpacity accessibilityRole="button" disabled={pending || !email.trim() || !password || (creating && !confirmation)} style={[s.submit, (pending || !email.trim() || !password) && {opacity: 0.45}]} onPress={() => void submit()}><Text style={s.submitText}>{pending ? 'Please wait…' : creating ? 'Create account' : 'Sign in'}</Text></TouchableOpacity>
    <TouchableOpacity accessibilityRole="button" disabled={pending} style={s.switchMode} onPress={() => {setCreating(value => !value); setError(null); setMessage(null);}}><Text style={{color: theme.accent}}>{creating ? 'Already have an account? Sign in' : 'Create an account'}</Text></TouchableOpacity>
  </View>;
}
const s = StyleSheet.create({
  form: {gap: 8}, heading: {color: theme.text, fontWeight: '700', marginTop: 8},
  input: {minHeight: 44, paddingHorizontal: 12, color: theme.text, backgroundColor: theme.bg, borderWidth: 1, borderColor: theme.borderLight, borderRadius: 8, fontSize: 16},
  submit: {minHeight: 44, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.accent, borderRadius: 8}, submitText: {color: theme.bg, fontWeight: '700'},
  switchMode: {minHeight: 44, justifyContent: 'center', alignItems: 'center'},
});

import { initializeApp, getApps } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: "***REDACTED_FIREBASE_KEY***",
  authDomain: "***REDACTED_PROJECT_ID***.firebaseapp.com",
  projectId: "***REDACTED_PROJECT_ID***",
  storageBucket: "***REDACTED_PROJECT_ID***.firebasestorage.app",
  messagingSenderId: "***REDACTED_PROJECT_NUMBER***",
  appId: "1:***REDACTED_PROJECT_NUMBER***:web:24fa9e0696b8e596d252ec",
};

// Initialize Firebase
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
const auth = getAuth(app);

export { auth, app };

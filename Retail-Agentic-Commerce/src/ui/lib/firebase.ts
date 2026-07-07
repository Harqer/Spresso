import { initializeApp, getApps } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: "AIzaSyDR_OnsPrOJsEYeG4F6u2T4D-hRRcELc8A",
  authDomain: "spresso-5561f.firebaseapp.com",
  projectId: "spresso-5561f",
  storageBucket: "spresso-5561f.firebasestorage.app",
  messagingSenderId: "656500460421",
  appId: "1:656500460421:web:24fa9e0696b8e596d252ec",
};

// Initialize Firebase
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
const auth = getAuth(app);

export { auth, app };

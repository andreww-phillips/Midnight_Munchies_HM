import React, { useEffect, useState } from "react";
import RestaurantList from "../restaurant_library/restaurant_library.jsx";
import { api } from "../../../api";

function readFile(file) {
  if (!file) return Promise.resolve(null);
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("Unable to read the selected file."));
    reader.readAsDataURL(file);
  });
}

export default function RegistrationPage() {
  const [isLogin, setIsLogin] = useState(true);
  const [user, setUser] = useState(null);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState({
    role: "",
    username: "",
    password: "",
    // Role-specific fields
    first_name: "",
    last_name: "",
    restaurant_name: "",
    work_id: "",
    address_longitude: 0.0,       
    address_latitude: 0.0,  
    status: "",
    menu: null, 
    logo: null
  });

  useEffect(() => {
    let active = true;
    api.currentUser().then((account) => { if (active) setUser(account); })
      .catch((sessionError) => {
        if (active && sessionError.status !== 401) setError(sessionError.message);
      });
    return () => { active = false; };
  }, []);

  // Handle standard text/select inputs
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // Handle file inputs separately
  const handleFileChange = (e) => {
    const { name, files } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: files[0],
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    if (!isLogin) {
      if (!formData.role) {
        setError("Please select a role.");
        return;
      }
      if (formData.role === "customer" &&
        (!formData.first_name || !formData.last_name ||
          formData.address_longitude === "" || formData.address_latitude === "")) {
        setError("Please complete all customer profile fields.");
        return;
      }
      if (formData.role === "courier" && !formData.work_id) {
        setError("Please complete all courier profile fields.");
        return;
      }
      if (formData.role === "restaurant" && !formData.restaurant_name.trim()) {
        setError("Please provide the restaurant name.");
        return;
      }
    }
    setIsSubmitting(true);
    try {
      if (isLogin) {
        setUser(await api.login(formData.username, formData.password));
      } else {
        const role = formData.role === "restaurant" ? "RESTAURANT_STAFF" : formData.role.toUpperCase();
        const details = formData.role === "restaurant" ? {
          restaurantName: formData.restaurant_name.trim(),
          restaurantLogo: await readFile(formData.logo),
          menu: await readFile(formData.menu),
        } : {};
        setUser(await api.register(formData.username, formData.password, role, details));
      }
    } catch (err) {
      setError(err.status === 401 ? "Invalid username or password." : err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    try {
      await api.logout();
    } catch (logoutError) {
      setError(logoutError.message);
      return;
    }
    setUser(null);
    setError("");
    setFormData({
      role: "",
      username: "",
      password: "",
      first_name: "",
      last_name: "",
      restaurant_name: "",
      work_id: "",
      address_longitude: "",
      address_latitude: "",
      status: "",
      menu: null,
      logo: null,
    });
  };

  if (user) {
    return (
      <div className="app-shell">
        <header className="topbar">
          <div>
            <p className="eyebrow">MIDNIGHT MUNCHIES</p>
            <h1>Welcome, {user.username}</h1>
            <p className="muted">Role: {user.role}</p>
          </div>
          <button className="secondary-button" type="button" onClick={handleLogout}>Log out</button>
        </header>
        {error && <p role="alert">{error}</p>}
        <RestaurantList user={user} />
      </div>
    );
  }

  return (
    <>
      <main className="auth-card">
        <p className="eyebrow">MIDNIGHT MUNCHIES</p>
        <h1>{isLogin ? "Welcome back" : "Create your account"}</h1>
        <p className="muted">Order your favourite food without the late-night wait.</p>
        <form id="registration-form-container" onSubmit={handleSubmit}>
        {!isLogin && <section id="role-selection-container">
          <label htmlFor="role-selection">Select Your Role:</label>
          <select id="role-selection" name="role" value ={formData.role} onChange={handleChange} disabled={isSubmitting} required>
              <option value="">Please select a role</option>
              <option value="customer">Customer</option>
              <option value="courier">Courier</option>
              <option value="restaurant">Restaurant Staff</option>
          </select>
        </section>}
          <div id="input-field">
            <label htmlFor="username">Username:</label>
            <input id="username" type="text" placeholder="Username" name="username" value={formData.username} onChange={handleChange} disabled={isSubmitting} required />
          </div>
          <div id="input-field">
            <label htmlFor="password">Password:</label>
            <input id="password" type="password" placeholder="Password" name="password" value={formData.password} onChange={handleChange} disabled={isSubmitting} required />
          </div>

          {!isLogin && formData.role === "customer" && (
            <>
              <div id="input-field" name="first_name">
                <label htmlFor="first_name">First Name:</label>
                <input id="first_name" type="text" placeholder="First Name" name="first_name" value={formData.first_name} onChange={handleChange} required />
              </div>
              <div id="input-field" name="last_name">
                <label htmlFor="last_name">Last Name:</label>
                <input id="last_name" type="text" placeholder="Last Name" name="last_name" value={formData.last_name} onChange={handleChange} required />
              </div>
              <div id="input-field" name="address_longitude">
                <label htmlFor="address_longitude">Address Longitude:</label>
                <input id="address_longitude" type="number" step="any" min="-180" max="180" placeholder="Address Longitude" name="address_longitude" value={formData.address_longitude} onChange={handleChange} required />
              </div>
              <div id="input-field" name="address_latitude">
                <label htmlFor="address_latitude">Address Latitude:</label>
                <input id="address_latitude" type="number" step="any" min="-90" max="90" placeholder="Address Latitude" name="address_latitude" value={formData.address_latitude} onChange={handleChange} required />
              </div>
            </>
            )}
            {!isLogin && formData.role === "restaurant" && (
            <>
              <div id="input-field" name="restaurant_name">
                <label htmlFor="restaurant_name">Restaurant Name:</label>
                <input id="restaurant_name" type="text" placeholder="Restaurant Name" name="restaurant_name" value={formData.restaurant_name} onChange={handleChange} required />
              </div>
              <div id="input-field" name="menu">
                <label htmlFor="menu">Menu file (optional):</label>
                <input id="menu" type="file" accept=".pdf,.doc,.docx,.txt" name="menu" onChange={handleFileChange} />
              </div>
              <div id="input-field" name="logo">
                <label htmlFor="logo">Logo (optional):</label>
                <input id="logo" type="file" accept=".jpg,.jpeg,.png,.gif,.pdf" name="logo" onChange={handleFileChange} />
              </div>
            </>
          )}
          {!isLogin && formData.role === "courier" && (
            <div id="input-field" name="work_id">
              <label htmlFor="work_id">Work ID:</label>
              <input
                id="work_id"
                type="number"
                placeholder="Work ID"
                name="work_id"
                value={formData.work_id}
                onChange={handleChange}
                required
              />
            </div>
          )}
          {error && <p role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Please wait..." : isLogin ? "Log in" : "Register"}
          </button>
          <button className="link-button" type="button" disabled={isSubmitting} onClick={() => { setIsLogin(!isLogin); setError(""); }}>
            {isLogin ? "Create an account" : "Use an existing account"}
          </button>
        </form>
      </main>
      <footer>
        <p>&copy; 2023 Midnight Munchies. All rights reserved.</p>
      </footer>
    </>
  );
}

import React, { useEffect, useState } from "react";

const API_URL = "http://localhost:8080/api/auth";

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
    id: "",
    address_longitude: 0.0,       
    address_latitude: 0.0,  
    status: "",
    menu: null, 
    logo: null
  });

  useEffect(() => {
    fetch(`${API_URL}/me`, { credentials: "include" })
      .then((response) => {
        if (response.ok) {
          return response.json().then(setUser);
        }
        return null;
      })
      .catch(() => setError("Unable to connect to the authentication service."));
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
    setIsSubmitting(true);
    try {
      const response = await fetch(`${API_URL}/${isLogin ? "login" : "register"}`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: formData.username,
          password: formData.password,
          role: formData.role ? formData.role.toUpperCase() : null
        })
      });
      if (!response.ok) {
        setError(isLogin ? "Invalid username or password." : "Registration could not be completed.");
        return;
      }
      setUser(await response.json());
    } catch {
      setError("Unable to connect to the authentication service.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    await fetch(`${API_URL}/logout`, { method: "POST", credentials: "include" });
    setUser(null);
    setError("");
  };

  if (user) {
    return (
      <main>
        <h3>Welcome, {user.username}</h3>
        <p>Role: {user.role}</p>
        <button type="button" onClick={handleLogout}>Log out</button>
      </main>
    );
  }

  return (
    <>
      <header />
      <nav />
      <main>
        <h3>{isLogin ? "Log in" : "Register"}</h3>
        {!isLogin && <section id="role-selection-container">
          <label>Select Your Role:</label>
          <select id="role-selection" name="role" value ={formData.role} onChange={handleChange}>
              <option value="">Please select a role</option>
              <option value="customer">Customer</option>
              <option value="courier">Courier</option>
              <option value="restaurant">Restaurant Staff</option>
          </select>
        </section>}
        <section id="registration-form-container">
          <div id="input-field">
            <label htmlFor="username">Username:</label>
            <input type="text" placeholder="Username" name="username" value={formData.username} onChange={handleChange} required />
          </div>
          <div id="input-field">
            <label htmlFor="password">Password:</label>
            <input type="password" placeholder="Password" name="password" value={formData.password} onChange={handleChange} required />
          </div>

          {!isLogin && formData.role === "customer" && (
            <>
              <div id="input-field" name="first_name">
                <label htmlFor="first_name">First Name:</label>
                <input type="text" placeholder="First Name" name="first_name" value={formData.first_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="last_name">
                <label htmlFor="last_name">Last Name:</label>
                <input type="text" placeholder="Last Name" name="last_name" value={formData.last_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="address_longitude">
                <label htmlFor="address_longitude">Address Longitude:</label>
                <input type="number" placeholder="Address Longitude" name="address_longitude" value={formData.address_longitude} onChange={handleChange} />
              </div>
              <div id="input-field" name="address_latitude">
                <label htmlFor="address_latitude">Address Latitude:</label>
                <input type="number" placeholder="Address Latitude" name="address_latitude" value={formData.address_latitude} onChange={handleChange} />
              </div>
            </>
            )}
            {!isLogin && formData.role === "restaurant" && (
            <>
              <div id="input-field" name="restaurant_name">
                <label htmlFor="restaurant_name">Restaurant Name:</label>
                <input type="text" placeholder="Restaurant Name" name="restaurant_name" value={formData.restaurant_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="menu">
                <label htmlFor="menu">Menu:</label>
                <input type="file" accept=".pdf,.doc,.docx,.txt" name="menu" onChange={handleFileChange} />
              </div>
              <div id="input-field" name="logo">
                <label htmlFor="logo">Logo:</label>
                <input type="file" accept=".jpg,.jpeg,.png,.gif,.pdf" name="logo" onChange={handleFileChange} />
              </div>
            </>
          )}
          {!isLogin && formData.role === "courier" && (
            <>
              <div id="input-field" name="first_name">
                <label htmlFor="first_name">First Name:</label>
                <input type="text" placeholder="First Name" name="first_name" value={formData.first_name} onChange={handleChange} />
              </div>
              <div id="input-field" name="last_name">
                <label htmlFor="last_name">Last Name:</label>
                <input type="text" placeholder="Last Name" name="last_name" value={formData.last_name} onChange={handleChange} />
              </div>
            </>
          )}
          {error && <p role="alert">{error}</p>}
          <button type="submit" onClick={handleSubmit} disabled={isSubmitting}>
            {isSubmitting ? "Please wait..." : isLogin ? "Log in" : "Register"}
          </button>
          <button type="button" onClick={() => { setIsLogin(!isLogin); setError(""); }}>
            {isLogin ? "Create an account" : "Use an existing account"}
          </button>
        </section> 
      </main>
      <footer />
    </>
  );
}
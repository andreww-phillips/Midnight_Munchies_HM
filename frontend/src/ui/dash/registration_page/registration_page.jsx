<<<<<<< Updated upstream
import React, { useEffect, useState } from "react";

const API_URL = "http://localhost:8080/api/auth";
=======
import React, { useState } from "react";
import { supabase } from "../../../supabaseClient";
>>>>>>> Stashed changes

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
<<<<<<< Updated upstream
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
=======
      setFormData((prev) => ({
        ...prev,
        [name]: files[0], 
      }));
    };

  const buildPayload = () => {
      const base = {
        email: formData.email,
        password: formData.password,
        role: formData.role,
      };

      switch (formData.role) {
        case "customer":
          return {
            ...base,
            first_name: formData.first_name,
            last_name: formData.last_name,
            delivery_longitude: parseFloat(formData.address_longitude),
            delivery_latitude: parseFloat(formData.address_latitude),
          };
        case "courier":
          return {
            ...base,
            work_id: parseInt(formData.work_id, 10),
            status: formData.status,
          };
        case "restaurant":
          return {
            ...base,
            restaurant_name: formData.restaurant_name,
            restaurant_longitude: parseFloat(formData.address_longitude),
            restaurant_latitude: parseFloat(formData.address_latitude),
            status: formData.status,
          };
        default:
          return null;
      }
  };   

const handleSubmit = async (e) => {
  e.preventDefault();

  // 1. Register the user with Supabase Auth (Supabase handles the password hashing securely!)
  const { data: authData, error: authError } = await supabase.auth.signUp({
    email: formData.email,
    password: formData.password,
  });

  if (authError) {
    console.error("Error signing up:", authError.message);
    return;
  }

  console.log("User registered successfully in Supabase Auth:", authData);

  const payload = buildPayload();
  console.log("Ready to send to database:", payload);
  
  // 2. Next: Save the rest of your form data (role, first_name, logo URL, etc.) 
  // into your custom database tables!
};
>>>>>>> Stashed changes

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
<<<<<<< Updated upstream
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
=======
        <h3>Register</h3>
        <form onSubmit={handleSubmit}>
          <section id="role-selection-container">
            <label>Select Your Role:</label>
            <select id="role-selection" name="role" value ={formData.role} onChange={handleChange}>
                <option value="">Please select a role</option>
                <option value="customer">Customer</option>
                <option value="courier">Courier</option>
                <option value="restaurant">Restaurant Staff</option>
            </select>
          </section>
          <section id="registration-form-container">
            <div id="input-field" name="email">
              <label htmlFor="email">Email:</label>
              <input type="email" placeholder="Email" name="email" value={formData.email} onChange={handleChange} />
            </div>
            <div id="input-field" name="password">
              <label htmlFor="password">Password:</label>
              <input type="password" placeholder="Password" name="password" value={formData.password} onChange={handleChange} />
            </div>
            {formData.role === "customer" && (
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
              {formData.role === "restaurant" && (
              <>
                <div id="input-field" name="restaurant_name">
                  <label htmlFor="restaurant_name">Restaurant Name:</label>
                  <input type="text" placeholder="Restaurant Name" name="restaurant_name" value={formData.restaurant_name} onChange={handleChange} />
                </div>
                <div id="input-field" name="address_longitude">
                  <label htmlFor="address_longitude">Address Longitude:</label>
                  <input type="number" placeholder="Address Longitude" name="address_longitude" value={formData.address_longitude} onChange={handleChange} />
                </div>
                <div id="input-field" name="address_latitude">
                  <label htmlFor="address_latitude">Address Latitude:</label>
                  <input type="number" placeholder="Address Latitude" name="address_latitude" value={formData.address_latitude} onChange={handleChange} />
                </div>
                <div id="input-field" name="status">
                  <label htmlFor="status">Status:</label>
                  <select id="status" name="status" value={formData.status} onChange={handleChange}>
                    <option value="">Select Status</option>
                    <option value="open">Open</option>
                    <option value="paused">Paused</option>
                    <option value="closed">Closed</option>
                  </select>
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
            {formData.role === "courier" && (
              <>
                <div id="input-field" name="work_id">
                  <label htmlFor="work_id">Work ID:</label>
                  <input type="text" placeholder="Work ID" name="work_id" value={formData.work_id} onChange={handleChange} />
                </div>
                <div id="input-field" name="address_longitude">
                  <label htmlFor="address_longitude">Address Longitude:</label>
                  <input type="number" placeholder="Address Longitude" name="address_longitude" value={formData.address_longitude} onChange={handleChange} />
                </div>
                <div id="input-field" name="address_latitude">
                  <label htmlFor="address_latitude">Address Latitude:</label>
                  <input type="number" placeholder="Address Latitude" name="address_latitude" value={formData.address_latitude} onChange={handleChange} />
                </div>
                <div>
                  <label htmlFor="status">Status:</label>
                  <select id="status" name="status" value={formData.status} onChange={handleChange}>
                    <option value="">Select Status</option>
                    <option value="available">Available</option>
                    <option value="unavailable">Unavailable</option>
                  </select>
                </div>
              </>
            )}
            <button type="submit" >Submit</button>
            <button type="button">Log-in</button>
          </section> 
        </form>
>>>>>>> Stashed changes
      </main>
      <footer>
        <p>&copy; 2023 Midnight Munchies. All rights reserved.</p>
      </footer>
    </>
  );
}
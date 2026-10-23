import React, { useEffect, useState } from "react";
import { supabase } from "../../../supabaseClient";

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
    setFormData((prev) => ({
      ...prev,
      [name]: files[0],
    }));
  };

  // Build role-specific payload for registration
  const buildPayload = (userId) => {
    const base = {
      id: userId,
      username: formData.username,
      password: formData.password,
    };

    switch (formData.role.toLowerCase()) {
      case "customer":
        return {
          ...base,
          first_name: formData.first_name,
          last_name: formData.last_name,
          delivery_longitude: parseFloat(formData.address_longitude || 0),
          delivery_latitude: parseFloat(formData.address_latitude || 0),
        };
      case "courier":
        return {
          ...base,
          first_name: formData.first_name,
          last_name: formData.last_name,
          work_id: formData.work_id,
          status: formData.status || "available",
        };
      case "restaurant":
        return {
          ...base,
          restaurant_name: formData.restaurant_name,
          restaurant_longitude: parseFloat(formData.address_longitude || 0),
          restaurant_latitude: parseFloat(formData.address_latitude || 0),
          status: formData.status || "open",
        };
      default:
        return base;
    }
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
          role: formData.role ? formData.role.toUpperCase() : null,
        }),
      });
      if (!response.ok) {
        setError(isLogin ? "Invalid username or password." : "Registration could not be completed.");
        return;
      }
      const userData = await response.json();

      if (isLogin) {
        setUser(userData);
        return;
      }

      const userId = userData.id || userData.username;

      // Handle Supabase Storage uploads for restaurant roles.
      let menuUrl = null;
      let logoUrl = null;

      if (formData.role === "restaurant") {
        try {
          if (formData.menu) {
            const menuPath = `${userId}/menu_${Date.now()}_${formData.menu.name}`;
            const { error: menuError } = await supabase.storage
              .from("menus")
              .upload(menuPath, formData.menu);

            if (!menuError) {
              const { data: menuPublic } = supabase.storage
                .from("menus")
                .getPublicUrl(menuPath);
              menuUrl = menuPublic.publicUrl;
            }
          }

          if (formData.logo) {
            const logoPath = `${userId}/logo_${Date.now()}_${formData.logo.name}`;
            const { error: logoError } = await supabase.storage
              .from("logos")
              .upload(logoPath, formData.logo);

            if (!logoError) {
              const { data: logoPublic } = supabase.storage
                .from("logos")
                .getPublicUrl(logoPath);
              logoUrl = logoPublic.publicUrl;
            }
          }
        } catch (uploadErr) {
          console.error("Storage upload error:", uploadErr);
        }
      }

      const payload = {
        ...buildPayload(userId),
        ...(formData.role === "restaurant" && {
          menu_url: menuUrl,
          logo_url: logoUrl,
        }),
      };

      const tableName =
        formData.role === "restaurant"
          ? "Restaurant"
          : formData.role === "courier"
            ? "Courrier_Info"
            : "Customer_Info";

      const { error: dbError } = await supabase
        .from(tableName)
        .insert([payload]);

      if (dbError) {
        console.error("Database insert error:", dbError.message);
      }

      setUser(userData);
    } catch (err) {
      console.error(err);
      setError("Unable to connect to the authentication service.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    await fetch(`${API_URL}/logout`, { method: "POST", credentials: "include" });
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
      <footer>
        <p>&copy; 2023 Midnight Munchies. All rights reserved.</p>
      </footer>
    </>
  );
}